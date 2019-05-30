package com.trade.market;


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.DoFn.ProcessContext;
import org.apache.beam.sdk.transforms.DoFn.ProcessElement;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

public class StreamProcessor implements Serializable{
	
	public static boolean headerAdded = false;
	
	public void processData(Map<String, String> dayReport, Pipeline pipeline) {
		
	   
        //PCollection<String> input1 =
          //      pipeline.apply(TextIO.read().from(myReceivedText));
        
		PCollection<String> input =
		        pipeline.apply(TextIO.read().from(System.getProperty("user.dir")+"/default.log"));

		    PCollection<KV<String, BigDecimal>> parseAndConvertToKV =
		        input.apply(
		            "ParseAndConvertToKV",
		            MapElements.via(
		                new SimpleFunction<String, KV<String, BigDecimal>>() {

		                  @Override
		                  public KV<String, BigDecimal> apply(String input) {
		                	
		                	//System.out.println("Input String:::"+input);  
		                    String[] split = input.split(",");
		                    if (split.length < 3) {
		                      return null;
		                    }
		                    String key = split[0];
		                    //key = key.replaceAll("^.|.$", "");
		                    String valueNew = split[1].replaceAll("^.|.$", "");
		                    
		                    BigDecimal value = new BigDecimal(valueNew);
		                    return KV.of(key.replaceAll("^.|.$", ""), value);
		                  }
		                }));

		    PCollection<KV<String, Iterable<BigDecimal>>> kvpCollection =
		        parseAndConvertToKV.apply(GroupByKey.<String, BigDecimal>create());
		    

		    PCollection<String> dayChange =
		        kvpCollection.apply(
		            "DayTradePerformance",
		            ParDo.of(
		                new DoFn<KV<String, Iterable<BigDecimal>>, String>() {

		                  @ProcessElement
		                  public void processElement(ProcessContext context) {
		                	BigDecimal lastTrade = new BigDecimal(0.0);
		                    String brand = context.element().getKey();
		                    Iterable<BigDecimal> sells = context.element().getValue();
		                    for (BigDecimal amount : sells) {
		                    	lastTrade = amount;
		                    }
		                    
		                    BigDecimal percentChange = new BigDecimal(0.0);
		                    String colorCode = null;
		                    
		                    if(dayReport.containsKey(brand)) {
		                    	BigDecimal previousDayValue = new BigDecimal(dayReport.get(brand));
		                    	
		                    	percentChange = lastTrade.divide(previousDayValue, 3, RoundingMode.CEILING); 
		                    	
		                    	int  result  = percentChange.compareTo(new BigDecimal(1.5));
		                    	if(result == 1) {
		                    		colorCode = "red";
		                    	}else {
		                    		result  = percentChange.compareTo(new BigDecimal(1));
		                    		if(result == 1) {
		                    			colorCode = "amber";
		                    		}else {
		                    			colorCode = "green";
		                    		}
		                    	}
		                    	
		                    }
		                    
		                    if(!headerAdded) {
		                    	context.output("Symbol" + ","+"Last Trade" +","+"% Change"+","+"Status"); 
		                    	
		                    }
		                    headerAdded = true;
		                    context.output(brand + ","+lastTrade +","+percentChange+","+colorCode);  
		                  }
		                }));

		    dayChange.apply(TextIO.write().to("trade_report").withoutSharding());

		    pipeline.run();
	}
	
}

