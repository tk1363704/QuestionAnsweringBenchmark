package org.hobbit.questionanswering;

import java.io.IOException;

import org.aksw.gerbil.datatypes.ExperimentType;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.hobbit.core.Commands;
import org.hobbit.core.components.AbstractBenchmarkController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QaBenchmark extends AbstractBenchmarkController {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(QaBenchmark.class);
	
	private static final String DATA_GENERATOR_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/cmartens/qadatagenerator";
	private static final String TASK_GENERATOR_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/cmartens/qataskgenerator";
	private static  String EVALUATION_MODULE_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/conrads/qaevaluationmodule";
	//private static  String EVALUATION_MODULE_CONTAINER_IMAGE = "git.project-hobbit.eu:4567/cmartens/qaevaluationmodule";
	
	protected static final String gerbilUri = "http://w3id.org/gerbil/vocab#";
	protected static final String gerbilQaUri = "http://w3id.org/gerbil/qa/hobbit/vocab#";
	protected static final Resource QA = resource("QA");
	protected static final Resource HYBRID = qaResource("hybridTask");
	protected static final Resource LARGESCALE = qaResource("largescaleTask");
	protected static final Resource MULTILINGUAL = qaResource("multilingualTask");
	protected static final Resource WIKIDATA = qaResource("wikidataTask");
	protected static final Resource TESTING = qaResource("testing");
	protected static final Resource TRAINING = qaResource("training");
	protected static final Resource ENGLISH = qaResource("EnLanguage");
	protected static final Resource FARSI = qaResource("FaLanguage");
	protected static final Resource GERMAN = qaResource("DeLanguage");
	protected static final Resource SPANISH = qaResource("EsLanguage");
	protected static final Resource ITALIAN = qaResource("ItLanguage");
	protected static final Resource FRENCH = qaResource("FrLanguage");
	protected static final Resource DUTCH = qaResource("NlLanguage");
	protected static final Resource ROMANIAN = qaResource("RoLanguage");
	
	private ExperimentType experimentType;
	
	private String experimentTaskName;
	private String experimentDataset;
	private String questionLanguage;
	private String sparqlService;
	
	private int numberOfQuestionSets;
	private int numberOfQuestions;
	private long timeForAnswering;
	private long seed;
	
	//create single data and task generator
	private int numberOfGenerators = 1;
	
	protected static final Resource resource(String local) {
        return ResourceFactory.createResource(gerbilUri + local);
    }
	
	protected static final Resource qaResource(String local) {
        return ResourceFactory.createResource(gerbilQaUri + local);
    }

	/**
	 * Initializes the Benchmark Controller by reading and checking all necessary information from the benchmark model.
	 */
    @Override
    public void init() throws Exception {
    	LOGGER.info("QaBenchmark: Initializing.");
    	super.init();
    	
    	experimentType = ExperimentType.QA;
    	
    	LOGGER.info("QaBenchmark: Loading parameters from benchmark model.");
        
        //load experimentTask from benchmark model
        NodeIterator iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty(gerbilQaUri+"hasExperimentTask"));
        if (iterator.hasNext()) {
            try {
            	Resource resource = iterator.next().asResource();
            	if (resource == null) { LOGGER.error("QaBenchmark: Got null resource."); }
            	String uri = resource.getURI();
            	if (HYBRID.getURI().equals(uri)) {
                    experimentTaskName = "hybrid";
                }
            	if (LARGESCALE.getURI().equals(uri)) {
                    experimentTaskName = "largescale";
                }
            	if (MULTILINGUAL.getURI().equals(uri)) {
                    experimentTaskName = "multilingual";
                }
            	if (WIKIDATA.getURI().equals(uri)) {
                    experimentTaskName = "wikidata";
                }
                LOGGER.info("QaBenchmark: Got experiment task from the parameter model: \""+experimentTaskName+"\"");
            } catch (Exception e) {
                LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
            }
        }
        //check experimentTaskName
        if(!experimentTaskName.equalsIgnoreCase("hybrid") && !experimentTaskName.equalsIgnoreCase("largescale") && !experimentTaskName.equalsIgnoreCase("multilingual") && !experimentTaskName.equalsIgnoreCase("wikidata")) {
        	String msg = "QaBenchmark: Couldn't get the experiment task from the parameter model. Must be \"hybrid\", \"largescale\" or \"multilingual\" or \"wikidata\". Aborting.";
        	LOGGER.error(msg);
        	throw new Exception(msg);
        }
        
        //load Dataset from benchmark model
        iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty(gerbilQaUri+"hasDataset"));
        if (iterator.hasNext()) {
            try {
            	Resource resource = iterator.next().asResource();
            	if (resource == null) { LOGGER.error("QaBenchmark: Got null resource."); }
            	String uri = resource.getURI();
            	if (TESTING.getURI().equalsIgnoreCase(uri)) {
            		experimentDataset = "testing";
                }
            	if (TRAINING.getURI().equalsIgnoreCase(uri)) {
            		experimentDataset = "training";
                }
                LOGGER.info("QaBenchmark: Got experiment dataset from the parameter model: \""+experimentDataset+"\"");
            } catch (Exception e) {
                LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
            }
        }
        //check Dataset
        if( !experimentDataset.equalsIgnoreCase("testing") && !experimentDataset.equalsIgnoreCase("training") ) {
        	String msg = "QaBenchmark: Couldn't get the dataset value from the parameter model. Aborting.";
        	LOGGER.error(msg);
        	throw new Exception(msg);
        }
        
        //load questionLanguage from benchmark model
        questionLanguage = "";
        if (!experimentTaskName.equalsIgnoreCase("multilingual")) {
        	questionLanguage = "en";
        	LOGGER.info("QaBenchmark: Setting question language to \"en\" due to experiment type is not \"multilingual\".");
        }else{
        	iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty(gerbilQaUri+"hasQuestionLanguage"));
        	if (iterator.hasNext()) {
                try {
                	Resource resource = iterator.next().asResource();
                	if (resource == null) { LOGGER.error("QaBenchmark: Got null resource."); }
                	String uri = resource.getURI();
                	if (ENGLISH.getURI().equals(uri)) {
                        questionLanguage = "en";
                    }
                	else if (GERMAN.getURI().equals(uri)) {
                        questionLanguage = "de";
                    }
                	else if (ITALIAN.getURI().equals(uri)) {
                        questionLanguage = "it";
                    }
                	else if (FRENCH.getURI().equals(uri)) {
                        questionLanguage = "fr";
                    }
                	else{
                    	questionLanguage = "en";
                    	LOGGER.info("QaBenchmark: Chosen question language is not supported yet. Setting question language to \""+questionLanguage+"\".");
                    }
                    LOGGER.info("QaBenchmark: Got question language from the parameter model: \""+questionLanguage+"\"");
                } catch (Exception e) {
                    LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
                }
            }
        }
        //check questionLanguage
        if(!questionLanguage.equalsIgnoreCase("en") && !questionLanguage.equalsIgnoreCase("fa") && !questionLanguage.equalsIgnoreCase("de") && !questionLanguage.equalsIgnoreCase("es") 
    			&& !questionLanguage.equalsIgnoreCase("it") && !questionLanguage.equalsIgnoreCase("fr") && !questionLanguage.equalsIgnoreCase("nl") && !questionLanguage.equalsIgnoreCase("ro")){
    		LOGGER.error("QaBenchmark: Couldn't get the right language from the parameter model. Must be one of: \"en\", \"fa\", \"de\", \"es\", \"it\", \"fr\", \"nl\", \"ro\". Using default value.");
    		questionLanguage = "en";
    		LOGGER.info("QaBenchmark: Setting language to default value: \"en\"");
    	}
        //yet supported languages
        if(!questionLanguage.equalsIgnoreCase("en") && !questionLanguage.equalsIgnoreCase("de")
        		&& !questionLanguage.equalsIgnoreCase("it") && !questionLanguage.equalsIgnoreCase("fr")){
        	LOGGER.error("QaBenchmark: Chosen language is not supported, yet.");
    		questionLanguage = "en";
    		LOGGER.info("QaBenchmark: Setting language to default value: \"en\"");
        }
        
        //load numberOfQuestionSets from benchmark model
        numberOfQuestionSets = -1;
        iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty(gerbilQaUri+"hasNumberOfQuestionSets"));
        if(iterator.hasNext()) {
        	try {
        		numberOfQuestionSets = iterator.next().asLiteral().getInt();
                LOGGER.info("QaBenchmark: Got number of question sets from the parameter model: \""+numberOfQuestionSets+"\"");
            } catch (Exception e) {
                LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
            }
        }
        //check numberOfQuestionSets, set numberOfQuestions
        if (numberOfQuestionSets <= 0) {
        	LOGGER.error("QaBenchmark: Couldn't get the number of question sets from the parameter model. Using default value.");
        	if(experimentTaskName.equals("largescale") && !experimentDataset.equalsIgnoreCase("training")){
        		numberOfQuestionSets = 30;
        	}else{
        		numberOfQuestionSets = 50;
        	}
        	LOGGER.info("QaBenchmark: Setting number of question sets to default value: \""+numberOfQuestionSets+"\"");
        }else{
        	if(experimentTaskName.equals("largescale") && !experimentDataset.equalsIgnoreCase("training")){
        		numberOfQuestions = (numberOfQuestionSets*((numberOfQuestionSets+1)))/2;
        		LOGGER.info("QaBenchmark: For large-scale, chosen number of "+numberOfQuestionSets+" question sets equals to "+numberOfQuestions+" questions.");
        	}else{
        		numberOfQuestions = numberOfQuestionSets;
        	}
        	LOGGER.info("QaBenchmark: Number of questions is set to \""+numberOfQuestions+"\".");
        }
        
        //load timeForAnswering from benchmark model
        timeForAnswering = -1;
        iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty(gerbilQaUri+"hasAnsweringTime"));
        if(iterator.hasNext()) {
        	try {
        		timeForAnswering = iterator.next().asLiteral().getLong();
                LOGGER.info("QaBenchmark: Got time for answering one question set from the parameter model: \""+timeForAnswering+"\"");
            } catch (Exception e) {
                LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
            }
        }
        //check timeForAnswering
        if (timeForAnswering < 0) {
        	LOGGER.error("QaBenchmark: Couldn't get the time for answering one question set from the parameter model. Using default value.");
        	timeForAnswering = 60000;
        	LOGGER.info("QaBenchmark: Setting time for answering one question set to default value: \""+timeForAnswering+"\"");
        }
        
        //load seed from benchmark model
        seed = -1;
        iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty("http://w3id.org/gerbil/qa/hobbit/vocab#hasSeed"));
        if(iterator.hasNext()) {
        	try {
        		seed = iterator.next().asLiteral().getLong();
                LOGGER.info("QaBenchmark: Got seed from the parameter model: \""+seed+"\"");
            } catch (Exception e) {
                LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
            }
        }
        //check seed
        if (seed < 0) {
        	LOGGER.error("QaBenchmark: Couldn't get the seed from the parameter model. Using default value.");
        	seed = 42;
        	LOGGER.info("QaBenchmark: Setting seed to default value: \""+seed+"\"");
        }
        
        //load SparqlService from benchmark model
        sparqlService = "";
    	iterator = benchmarkParamModel.listObjectsOfProperty(benchmarkParamModel.getProperty(gerbilQaUri+"hasSparqlService"));
        if (iterator.hasNext()) {
            try {
            	sparqlService = iterator.next().asLiteral().getString();
            	LOGGER.info("QaBenchmark: Got SPARQL service from the parameter model: \""+sparqlService+"\"");
            } catch (Exception e) {
                LOGGER.error("QaBenchmark: Exception while parsing parameter.", e);
            }
        }
        //check SparqlService
        try{
		    String query = "PREFIX dbo: <http://dbpedia.org/ontology/> PREFIX dbr: <http://dbpedia.org/resource/> ask where { dbr:DBpedia dbo:license dbr:GNU_General_Public_License . }";
		    QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlService, query);
		    qexec.execAsk();
        }catch(Exception e){
        	String msg = "QaBenchmark: SPARQL service can not be accessed. Aborting.";
        	LOGGER.error(msg, e);
        	throw new Exception(msg, e);
        }

        //create data generator
        LOGGER.info("QaBenchmark: Creating Data Generator "+DATA_GENERATOR_CONTAINER_IMAGE+".");
        String[] envVariables = new String[]{
        		QaDataGenerator.EXPERIMENT_TYPE_PARAMETER_KEY + "=" + experimentType.getName(),
        		QaDataGenerator.EXPERIMENT_TASK_PARAMETER_KEY + "=" + experimentTaskName,
        		QaDataGenerator.QUESTION_LANGUAGE_PARAMETER_KEY + "=" + questionLanguage,
        		QaDataGenerator.NUMBER_OF_QUESTIONS_PARAMETER_KEY + "=" + numberOfQuestions,
                QaDataGenerator.SEED_PARAMETER_KEY + "=" + seed,
                QaDataGenerator.SPARQL_SERVICE_PARAMETER_KEY + "=" + sparqlService,
                QaDataGenerator.DATASET_PARAMETER_KEY + "=" + experimentDataset};
        createDataGenerators(DATA_GENERATOR_CONTAINER_IMAGE, numberOfGenerators, envVariables);

        //create task generator
        LOGGER.info("QaBenchmark: Creating Task Generator "+TASK_GENERATOR_CONTAINER_IMAGE+".");
        envVariables = new String[] {
        		QaTaskGenerator.EXPERIMENT_TYPE_PARAMETER_KEY + "=" + experimentType.getName(),
        		QaTaskGenerator.EXPERIMENT_TASK_PARAMETER_KEY + "=" + experimentTaskName,
        		QaTaskGenerator.QUESTION_LANGUAGE_PARAMETER_KEY + "=" + questionLanguage,
        		QaTaskGenerator.NUMBER_OF_QUESTIONS_PARAMETER_KEY + "=" + numberOfQuestions,
        		QaTaskGenerator.TIME_FOR_ANSWERING_PARAMETER_KEY + "=" + timeForAnswering,
				QaTaskGenerator.SEED_PARAMETER_KEY + "=" + seed,
				QaTaskGenerator.DATASET_PARAMETER_KEY + "=" + experimentDataset};
        createTaskGenerators(TASK_GENERATOR_CONTAINER_IMAGE, numberOfGenerators, envVariables);

        //create evaluation storage
        LOGGER.info("QaBenchmark: Creating Default Evaluation Storage "+DEFAULT_EVAL_STORAGE_IMAGE+".");
        createEvaluationStorage();

        //wait for all components to finish their initialization
        LOGGER.info("QaBenchmark: Waiting for components to finish their initialization.");
        waitForComponentsToInitialize();
        
        LOGGER.info("QaBenchmark: Initialized.");
    }
	
    /**
     * Executes the benchmark.
     */
	@Override
    protected void executeBenchmark() throws Exception {
		LOGGER.info("QaBenchmark: Executing benchmark.");

        sendToCmdQueue(Commands.TASK_GENERATOR_START_SIGNAL);
        sendToCmdQueue(Commands.DATA_GENERATOR_START_SIGNAL);

        LOGGER.info("QaBenchmark: Waiting for Generators to finish.");
        waitForDataGenToFinish();
        waitForTaskGenToFinish();
        LOGGER.info("QaBenchmark: Waiting for System to finish.");
        if(experimentTaskName.equalsIgnoreCase("largescale")){
        	waitForSystemToFinish(60000); //wait up to 1 more minute
        }else{
        	waitForSystemToFinish(600000); //wait up to 10 more minutes
        }
        
        LOGGER.info("QaBenchmark: Creating Evaluation Module "+EVALUATION_MODULE_CONTAINER_IMAGE+" and waiting for evaluation components to finish.");
        createEvaluationModule(EVALUATION_MODULE_CONTAINER_IMAGE, new String[] { "qa.experiment_type" + "=" + experimentType.name() });
        waitForEvalComponentsToFinish();
        
        LOGGER.info("QaBenchmark: Sending result model.");
        sendResultModel(this.resultModel);
        
        LOGGER.info("QaBenchmark: Benchmark executed.");
    }
	
	/**
	 * Calls super.close() Method and logs Closing-Information.
	 */
	@Override
    public void close() throws IOException {
		LOGGER.info("QaBenchmark: Closing.");
        super.close();
        LOGGER.info("QaBenchmark: Closed.");
    }
}