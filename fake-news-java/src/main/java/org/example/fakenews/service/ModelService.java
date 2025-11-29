package org.example.fakenews.service;

import weka.classifiers.functions.Logistic;
import weka.core.SerializationHelper;
import weka.core.tokenizers.WordTokenizer;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
/**
 * Trains TF-IDF + Logistic Regression from data/dataset.csv and serves predictions.
 * Saves/loads the model graph (filter + classifier) at models/news_model.model.
 */
@Service
public class ModelService {

    @Value("${data.csv.path:data/dataset.csv}")
    private String dataCsvPath;

    @Value("${model.path:models/news_model.model}")
    private String modelPath;

    @Value("${tfidf.maxFeatures:20000}")
    private int maxFeatures;

    @Value("${tfidf.minTermFreq:1}")
    private int minTermFreq;

    @Value("${tfidf.useIdf:true}")
    private boolean useIdf;

    @Value("${tfidf.outputWordCounts:false}")
    private boolean outputWordCounts;

    private Filter textFilter;         // StringToWordVector (TF-IDF)
    private Logistic classifier;       // Logistic Regression
    private Instances header;          // Structure of attributes

    private static final Pattern URL_PATTERN = Pattern.compile("http\\S+|www\\.\\S+");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9\\s]+");
    private static final Pattern MULTISPACE_PATTERN = Pattern.compile("\\s+");

    @PostConstruct
    public void init() throws Exception {
        ensureDirs();
        if (new File(modelPath).exists()) {
            loadModel();
        } else {
            trainAndSave();
        }
    }

    private void ensureDirs() throws Exception {
        File modelFile = new File(modelPath);
        File modelsDir = modelFile.getParentFile();
        if (modelsDir != null && !modelsDir.exists()) {
            modelsDir.mkdirs();
        }
        File dataFile = new File(dataCsvPath);
        File dataDir = dataFile.getParentFile();
        if (dataDir != null && !dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    private void trainAndSave() throws Exception {
        if (!Files.exists(Paths.get(dataCsvPath))) {
            throw new IllegalStateException("Dataset not found at " + dataCsvPath);
        }

        // Load CSV: expect columns "text","label" where label is 0/1 or "real"/"fake"
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(dataCsvPath));
        Instances raw = loader.getDataSet();

        // Validate columns
        int textIdx = raw.attribute("text") != null ? raw.attribute("text").index() : -1;
        int labelIdx = raw.attribute("label") != null ? raw.attribute("label").index() : -1;
        if (textIdx == -1 || labelIdx == -1) {
            throw new IllegalArgumentException("CSV must contain 'text' and 'label' columns");
        }

        // Convert label to nominal if needed
        Attribute textAttr = raw.attribute(textIdx);
        Attribute labelAttr = raw.attribute(labelIdx);

        // Create new structure: [text(string), label(nominal)]
        FastVector labels = new FastVector();
        labels.addElement("real");
        labels.addElement("fake");
        Attribute newTextAttr = new Attribute("text", (FastVector) null); // string attribute
        Attribute newLabelAttr = new Attribute("label", labels);

        FastVector attrs = new FastVector();
        attrs.addElement(newTextAttr);
        attrs.addElement(newLabelAttr);
        Instances data = new Instances("news_data", attrs, raw.numInstances());

        // Populate instances and map label values
        for (int i = 0; i < raw.numInstances(); i++) {
            Instance r = raw.instance(i);
            String textVal = r.stringValue(textAttr);
            double labelVal;
            if (labelAttr.isNumeric()) {
                labelVal = r.value(labelAttr) == 1.0 ? 1.0 : 0.0;
            } else {
                String s = r.stringValue(labelAttr).toLowerCase().trim();
                labelVal = ("fake".equals(s) || "1".equals(s)) ? 1.0 : 0.0;
            }
            DenseInstance inst = new DenseInstance(2);
            inst.setValue(newTextAttr, preprocess(textVal));
            inst.setValue(newLabelAttr, labelVal);
            data.add(inst);
        }

        data.setClassIndex(data.attribute("label").index());

        // Configure TF-IDF filter
        StringToWordVector stwv = new StringToWordVector();
        stwv.setWordsToKeep(maxFeatures);
        stwv.setMinTermFreq(minTermFreq);
        stwv.setIDFTransform(useIdf);
        stwv.setTFTransform(true);
        stwv.setOutputWordCounts(outputWordCounts);
        stwv.setLowerCaseTokens(true);
        WordTokenizer tokenizer = new WordTokenizer();
        tokenizer.setDelimiters(" \t\n\r\f"); // whitespace tokenizer
        stwv.setTokenizer(tokenizer);
        stwv.setAttributeIndices("first");    // only transform the first attribute (text)

        // Fit filter
        stwv.setInputFormat(data);
        Instances filtered = Filter.useFilter(data, stwv);

        // Train classifier
        Logistic logReg = new Logistic();
        logReg.buildClassifier(filtered);

        // Save model graph (filter + classifier + header)
        this.textFilter = stwv;
        this.classifier = logReg;
        this.header = new Instances(filtered, 0);
        saveModel();
    }

    private void saveModel() throws Exception {
        Object[] modelBundle = new Object[]{textFilter, classifier, header};
        SerializationHelper.write(modelPath, modelBundle);
    }

    private void loadModel() throws Exception {
        Object[] modelBundle = (Object[]) SerializationHelper.read(modelPath);
        this.textFilter = (Filter) modelBundle[0];
        this.classifier = (Logistic) modelBundle[1];
        this.header = (Instances) modelBundle[2];
    }

    private String preprocess(String text) {
        if (text == null) return "";
        String t = text.toLowerCase();
        t = URL_PATTERN.matcher(t).replaceAll(" ");
        t = NON_ALNUM_PATTERN.matcher(t).replaceAll(" ");
        t = MULTISPACE_PATTERN.matcher(t).replaceAll(" ").trim();
        return t;
    }

    /**
     * Predict label and score for a single text.
     * Returns label ("real"/"fake") and confidence (probability of predicted class).
     */
    public Prediction predict(String text) throws Exception {
        String cleaned = preprocess(text);

        // Build instance with same structure as training
        Instance inst = new DenseInstance(header.numAttributes());
        inst.setDataset(header);

        // We need to create a temporary "string" dataset to run through the filter
        // 1) create structure with original text attribute
        Attribute textAttr = new Attribute("text", (FastVector) null);
        FastVector labels = new FastVector();
        labels.addElement("real");
        labels.addElement("fake");
        Attribute labelAttr = new Attribute("label", labels);
        FastVector attrs = new FastVector();
        attrs.addElement(textAttr);
        attrs.addElement(labelAttr);
        Instances tmp = new Instances("tmp_pred", attrs, 1);
        tmp.setClassIndex(1);

        DenseInstance rawInst = new DenseInstance(2);
        rawInst.setValue(textAttr, cleaned);
        rawInst.setValue(labelAttr, 0.0); // dummy class
        tmp.add(rawInst);

        // Apply the trained filter to get feature vector
        Instances vec = Filter.useFilter(tmp, textFilter);

        double[] dist = classifier.distributionForInstance(vec.instance(0));
        int predIdx = dist[1] >= dist[0] ? 1 : 0;
        String label = predIdx == 1 ? "fake" : "real";
        double score = predIdx == 1 ? dist[1] : dist[0];

        return new Prediction(label, score);
    }

    // Simple holder
    public static class Prediction {
        public final String label;
        public final double score;

        public Prediction(String label, double score) {
            this.label = label;
            this.score = score;
        }
    }
}