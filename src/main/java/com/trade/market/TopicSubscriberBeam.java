package com.trade.market;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */



import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Instant;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.SerializableCoder;
//import org.apache.beam.sdk.extensions.jackson.ParseJsons;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.DoFn.ProcessContext;
import org.apache.beam.sdk.transforms.DoFn.ProcessElement;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.util.Transport;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLContentMessage;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.services.bigquery.model.JsonObject;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
//import org.apache.beam.sdk.extensions.jackson.ParseJsons;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class TopicSubscriberBeam implements Serializable{
	
   public static void main(String... args) throws JCSMPException {
    	
    	List<MarketData> marketDataList = new ArrayList<>();
    	Map<String, String> marketDataMap = new HashMap<String, String>();

        // Check command line arguments
        if (args.length < 2 || args[1].split("@").length != 2) {
            System.out.println("Usage: TopicSubscriber <host:port> <client-username@message-vpn> [client-password]");
            System.out.println();
            System.exit(-1);
        }
        if (args[1].split("@")[0].isEmpty()) {
            System.out.println("No client-username entered");
            System.out.println();
            System.exit(-1);
        }
        if (args[1].split("@")[1].isEmpty()) {
            System.out.println("No message-vpn entered");
            System.out.println();
            System.exit(-1);
        }

        System.out.println("TopicSubscriber initializing...");
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);     // host:port
        properties.setProperty(JCSMPProperties.USERNAME, args[1].split("@")[0]); // client-username
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1].split("@")[1]); // message-vpn
        if (args.length > 2) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[2]); // client-password
        }
        final Topic topic = JCSMPFactory.onlyInstance().createTopic("MD/>");
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);

        session.connect();

        final CountDownLatch latch = new CountDownLatch(1); // used for
                                                            // synchronizing b/w threads
        /** Anonymous inner-class for MessageListener
         *  This demonstrates the async threaded message callback */
        final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {
                if (msg instanceof TextMessage) {
                    System.out.printf("TextMessage received: '%s'%n",
                            ((TextMessage)msg).getText());
                } else {
                    System.out.println("Message received.");
                    if(msg.getAttachmentByteBuffer() != null) {
                        byte[] messageBinaryPayload = msg.getAttachmentByteBuffer().array();
                        try {
                            String myReceivedText = new String(messageBinaryPayload, "UTF-8");
                            
                            //NSE Data
                            String csvFile = System.getProperty("user.dir")+"/data.csv";
                            
                    		String line = null;
                    		Scanner scanner = null;
                    		int index = 0;
                    		
                    		BufferedReader reader = new BufferedReader(new FileReader(
                    				csvFile));

                    		String key = null;
                			String value = null;
                			
                    		while ((line = reader.readLine()) != null) {
                    			
                    			MarketData marketData = new MarketData();
                    			String newLine = (line.replaceAll("\",\"", ",,")).replaceAll("^\"|\"$", "");
                    			
                    			scanner = new Scanner(newLine);
                    			scanner.useDelimiter(",,");
                    			while (scanner.hasNext()) {
                    				String data = scanner.next();
                    				
                    				if (index == 0) {
                    					marketData.setSymbol(data);
                    					key = data;
                    				}	
                    					
                    				else if (index == 4) {
                    					marketData.setLastTraded(data);
                    					value = data;
                    					//System.out.println(key+"-----"+data);
                    				}
                    					
                    				else if (index == 6)
                    					marketData.setPercentChange(data);
                    				
                    				index++;
                    			}
                    			if(key!=null && value!=null)
                    				marketDataMap.put(key, value.replace(",", ""));
                    			index = 0;
                    			marketDataList.add(marketData);
                    		}
                    		
                    		//close reader
                    		reader.close();
                    		
                    		
                           
                            JsonParser parser = new JsonParser();
                            JsonElement jsonTree = parser.parse(myReceivedText);
                            //System.out.println("JSON Array = " + jsonTree.isJsonArray());
                            JsonElement jsonObjElem = jsonTree.getAsJsonArray().get(0);
                            JsonElement security = null;
                            JsonElement price = null;
                            JsonElement exchange = null;
                            if(jsonObjElem.isJsonObject()) {
                                com.google.gson.JsonObject jsonObject = jsonObjElem.getAsJsonObject();
                                security = jsonObject.get("Sec");
                                price = jsonObject.get("Price");
                                exchange = jsonObject.get("Ex");
                                System.out.println("Security = " + security.getAsString());
                                System.out.println("Price = " + price.getAsString());
                                System.out.println("Exchange = " + exchange.getAsString());
                                
                            }
                            
                            boolean append = true;
                            FileHandler handler = new FileHandler(System.getProperty("user.dir")+"/default.log", 1000000, 1, append);
                            
                            handler.setFormatter(new SimpleFormatter());
                            Logger logger = Logger.getLogger("com.solace.samples");
                            logger.addHandler(handler);
                            handler.setFormatter(new MyCustomFormatter());
                            logger.info(security+","+price+","+exchange);
                            handler.flush();
                            handler.close();
                            
                            //System.out.println("Arguments::"+args[0]+","+args[1]+","+args[2]+","+args[3]);
                            
                            Pipeline pipeline = null;
                            
                            if (args.length <= 3 || (args[3].isEmpty() && args[4].isEmpty())) {
                                System.out.println("No runner information entered");
                                pipeline = Pipeline.create();
                            }else {
                            	String [] pipelineOrgs = {args[3], args[4]};
                                StreamOptions options =
                                        PipelineOptionsFactory.fromArgs(pipelineOrgs).withValidation().as(StreamOptions.class);
                                pipeline = Pipeline.create(options);
                            }
                            
                            
                            StreamProcessor sp = new StreamProcessor();
                            sp.processData(marketDataMap, pipeline);
                            
                            
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        } catch (SecurityException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                    }
                    
                }
                System.out.printf("Message Dump:%n%s%n",msg.dump());
                //latch.countDown();  // unblock main thread
            }

            @Override
            public void onException(JCSMPException e) {
                System.out.printf("Consumer received exception: %s%n",e);
                latch.countDown();  // unblock main thread
            }
        });
        session.addSubscription(topic);
        System.out.println("Connected. Awaiting message...");
        cons.start();
        // Consume-only session is now hooked up and running!

        try {
        	System.out.println(latch.getCount());
        	
            latch.await(); // block here until message received, and latch will flip
            
        } catch (InterruptedException e) {
            System.out.println("I was awoken while waiting");
        }
        // Close consumer
        cons.close();
        //System.out.println("Exiting.");
        session.closeSession();
        
    }
    
    
    static class ParseTableRowJson extends SimpleFunction<String, TableRow> {
        @Override
        public TableRow apply(String input) {
          try {
            return Transport.getJsonFactory().fromString(input, TableRow.class);
          } catch (IOException e) {
            throw new RuntimeException("Failed parsing table row json", e);
          }
        }
      }
    
    private static class MyCustomFormatter extends Formatter {
    	 
        @Override
        public String format(LogRecord record) {
            StringBuffer sb = new StringBuffer();
            sb.append(record.getMessage());
            sb.append("\n");
            return sb.toString();
        }
         
    }
    
    public interface StreamOptions extends PipelineOptions {

	    
	}
    
    
}
