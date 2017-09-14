/*
 * Copyright © 2009-2017 The Apromore Initiative.
 *
 * This file is part of "Apromore".
 *
 * "Apromore" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * "Apromore" is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/lgpl-3.0.html>.
 */

package org.apromore.plugin.portal.predictivemonitor;

// Java 2 Standard Edition
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

// Third party packages
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.deckfour.xes.model.XAttributable;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Dataflow implements Closeable {

    private static Logger LOGGER = LoggerFactory.getLogger(Dataflow.class.getCanonicalName());

    final private List<Process> processors = new ArrayList<>();
    private       String        sinkTopic;
    final private Set<String>   topicNames = new HashSet<>();
    private       String        kafkaHost  = "localhost:9092";
    private       File          nirdizatiDirectory;

    /**
     * Create a Nirdizati dataflow.
     *
     * The dataflow consists of various Kafka topics and python-based processor processes.
     *
     * @param logName  suffix for generated Kafka topics, e.g. "bpi_12"
     * @param tag  subfield to distinguish predictor training set data files, e.g. "bpi12"
     * @param nirdizatiDirectory
     */
    Dataflow(String logName, String tag, File nirdizatiDirectory) {
        LOGGER.info("Create dataflow for log named " + logName + " with tag " + tag);

        this.nirdizatiDirectory = nirdizatiDirectory;
        this.sinkTopic          = "events_" + logName;
        String prefixesTopic    = "prefixes_" + logName;
        String predictionsTopic = "predictions_" + logName;

        Properties props = new Properties();
        props.put("bootstrap.servers",  kafkaHost);

        try (AdminClient adminClient = AdminClient.create(props)) {
            ListTopicsResult result = adminClient.listTopics();
            Set<String> nameSet = result.names().get();
            LOGGER.info("Topic names: " + nameSet);
            topicNames.addAll(Arrays.asList(sinkTopic, prefixesTopic, predictionsTopic, "events_with_predictions"));
            LOGGER.info("Creating topics " + topicNames);
            List<NewTopic> topics = new ArrayList<>();
            for (String topicName: topicNames) {
                if (nameSet.contains(topicName)) {
                    LOGGER.info("Topic " + topicName + " already exists");
                } else {
                    topics.add(new NewTopic(topicName, 1, (short) 1));
                }
            }
            adminClient.createTopics(topics);
            LOGGER.info("Created topics " + topics);

        } catch (ExecutionException | InterruptedException e) {
            LOGGER.warn("Unable to list topics", e);
        }

        createProcessor("python", "PredictiveMethods/collate-events.py",                               kafkaHost, sinkTopic, prefixesTopic);
        createProcessor("python", "PredictiveMethods/CaseOutcome/case-outcome-kafka-processor.py",     kafkaHost, prefixesTopic, predictionsTopic, tag, "label", "slow_probability");
        //createProcessor("python", "PredictiveMethods/CaseOutcome/case-outcome-kafka-processor.py",     kafkaHost, prefixesTopic, predictionsTopic, tag, "label2", "rejected_probability");
        createProcessor("python", "PredictiveMethods/RemainingTime/remaining-time-kafka-processor.py", kafkaHost, prefixesTopic, predictionsTopic, tag);
        createProcessor("python", "PredictiveMethods/join-events-to-predictions.py",                   kafkaHost, prefixesTopic, predictionsTopic, "events_with_predictions", Integer.toUnsignedString(2));
        createProcessor("node", "server-kafka.js");
    }

    /**
     * Spawn a process, recording it in <var>processes</var> so that the {@link #close} method can kill it later.
     */
    private void createProcessor(String... args) {
        try {
            LOGGER.info("Launching processor: " + args);
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(nirdizatiDirectory);
            pb.redirectError(new File("/tmp/error.txt"));
            pb.redirectOutput(new File("/tmp/output.txt"));
            Process p = pb.start();
            processors.add(p);
            LOGGER.info("Launched processor");

        } catch (IOException e) {
            LOGGER.warn("Unable to create processor", e);
        }
    }

    /**
     * Feed a log into the sink topic of the dataflow.
     *
     * @param log  an OpenXES log in Ilya's particular format
     */
    void exportLog(XLog log) {
    
        // Configuration
        Properties props = new Properties();
        props.put("bootstrap.servers",  kafkaHost);
        props.put("acks",               "all");
        props.put("retries",            0);
        props.put("batch.size",         16384);
        props.put("linger.ms",          1);
        //props.put("timeout.ms",         5000);
        props.put("max.block.ms",       5000);
        //props.put("metadata.fetch.timeout.ms", 5000);
        //props.put("request.timeout.ms", 5000);
        props.put("buffer.memory",      33554432);
        props.put("key.serializer",     StringSerializer.class);
        props.put("value.serializer",   StringSerializer.class);
        
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            // TODO: create the Kafka topic if it doesn't exist
            
            // Merge the XES events of all traces into a single list of JSON objects
            List<JSONObject> jsons = new ArrayList<>();
            for (XTrace trace: log) {
                for (XEvent event: trace) {
                    JSONObject json = new JSONObject();
                    json.put("log", "bpi_12");
                    json.put("sequence_nr", trace.getAttributes().get("concept:name").toString());
                    json.put("remtime", 0); 
                    addAttributes(log, json);
                    addAttributes(trace, json);
                    addAttributes(event, json);
                    jsons.add(json);
                }   
            }   
            
            // Sort the events by time
            final DatatypeFactory f = DatatypeFactory.newInstance();
            Collections.sort(jsons, new Comparator<JSONObject>() {
                public int compare(JSONObject a, JSONObject b) { 
                    return f.newXMLGregorianCalendar(a.optString("time")).compare(f.newXMLGregorianCalendar(b.optString("time")));
                }   
            }); 
            
            // Export to the Kafka topic
            for (JSONObject json: jsons) {
                LOGGER.info("  JSON: " + json.get("time"));
                producer.send(new ProducerRecord<String,String>(sinkTopic, json.toString()));
            }   
            
        } catch (DatatypeConfigurationException | JSONException e) {
            //Messagebox.show("Unable to export log", "Attention", Messagebox.OK, Messagebox.ERROR);
            LOGGER.error("Unable to export log to Kafka topic", e);
            return;
        }   
    }  

    private static void addAttributes(XAttributable attributable, JSONObject json) throws JSONException {
        for (Map.Entry<String, XAttribute> entry: attributable.getAttributes().entrySet()) {
            switch (entry.getKey()) {
            case "concept:name":
            case "creator":
            case "library":
            case "lifecycle:model":
            case "lifecycle:transition":
            case "org:resource":
            case "variant":
            case "variant-index":
                break;
            case "time:timestamp":
                json.put("time", entry.getValue().toString());
                break;
            default:
                json.put(entry.getKey(), entry.getValue().toString());
                break;
            }
        }
    }

    // Implementation of Closeable

    /**
     * Kill all the processors and delete all the topics.
     */
    public void close() {

        // Kill the processors
        while (!processors.isEmpty()) {
            Process p = processors.get(0);
            LOGGER.info("Killing process " + p + ", alive? " + p.isAlive());
            p.destroy();
            try {
                int code = p.waitFor();
                LOGGER.info("Killed process with return code " + code);
                processors.remove(p);

            } catch (InterruptedException | RuntimeException e) {
                LOGGER.warn("Unable to kill process", e);
            }
        }

        // Delete the topics
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaHost);

        try (AdminClient adminClient = AdminClient.create(props)) {
            ListTopicsResult result = adminClient.listTopics();
            Set<String> nameSet = result.names().get();
            LOGGER.info("Topic names reported by Kafka: " + nameSet);
            LOGGER.info("Topic names in this dataflow: " + topicNames);
            DeleteTopicsResult result2 = adminClient.deleteTopics(topicNames);
            result2.all().get();
            LOGGER.info("Deleted topics" + topicNames);
            topicNames.clear();
        }
        catch (ExecutionException | InterruptedException e) {
            LOGGER.warn("Unable to list topics", e);
        }
    }
}