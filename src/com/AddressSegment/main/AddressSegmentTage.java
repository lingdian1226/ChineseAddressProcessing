package com.AddressSegment.main;


import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;  
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;  

import com.AddressSegment.logic.AddressSplitImpl;
import com.AddressSegment.logic.UndefinedWordRecognize;
import com.AddressSegment.logic.service.AddressTageMaking;
import com.AddressSegment.metadata.model.CharDictionary;
import com.AddressSegment.metadata.model.WordDictionary;
import com.AddressSegment.tool.dao.impl.DictionaryFileOperationDAOImpl;
import com.AddressSegment.tool.dao.impl.TextPair;
import com.AddressSegment.util.Config;


public class AddressSegmentTage {

	public static class AddressMap extends Mapper<Object, Text, TextPair, IntWritable> {
		private final static IntWritable one = new IntWritable(1);
		public static FileSystem fs = null;
		public static DictionaryFileOperationDAOImpl DF = null;
		public static WordDictionary wordDict = null;
		public static CharDictionary<String> charDict = null;
		
		protected void setup(Context context) throws IOException {
			wordDict = new WordDictionary();
			charDict = new CharDictionary<String>();
			Configuration conf = new Configuration();
			fs = FileSystem.get(URI.create("hdfs://192.168.31.172:9000"), conf);
			try {
				DF = new DictionaryFileOperationDAOImpl(
						Config.getDefaultDictionaryHDFSURL(), Config.getCharDictionaryHDFSURL(), fs);
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//DictionaryFileOperationDAOImpl DF = new DictionaryFileOperationDAOImpl(
			//		Config.getDefaultDictionaryURL(), Config.getCharDictionaryURL());
			DF.putFileToDict(wordDict, charDict);
			System.out.println("********Setup Complete!********");
			
		}

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			
			String[] strArgs;
			AddressSplitImpl address = new AddressSplitImpl();
			UndefinedWordRecognize uwr = new UndefinedWordRecognize();
			TextPair Tp = new TextPair();
			AddressTageMaking Tage = new AddressTageMaking();
			
			//split the parameter to key and value
			
			strArgs = value.toString().split(",");
			
			//Text id = new Text(strArgs[0]);
			if (2 > strArgs.length) {
				//System.out.println(strArgs[0] + "," + strArgs[1]);
				System.out.println("No Value!");
				return;
			} else {
				ArrayList<String> wordArray = null;
				try {
					wordArray = address.Split(strArgs[1], wordDict, charDict, fs);
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} //Do the word segment
				wordArray = uwr.getUndefinedWord(wordArray);             //Do the unidentifiedword recognizing
				
				
				for (int i = 1; i < wordArray.size(); i++) {
					Text tmp = new Text(Tage.TageMaking(wordArray.get(i-1)));
					Text tmp1 = new Text(Tage.TageMaking(wordArray.get(i)));
					Tp.set(tmp, tmp1);
					//tmp.set(new Text(wordArray1.get(i)));
					context.write(Tp, one);
				}
				if(wordArray.size() > 1){
					Tp.set(new Text(Tage.TageMaking(wordArray.get(wordArray.size() - 1))), new Text(""));
					context.write(Tp, one);
				}
			}
		}
	}
	
	public static class AddressReduce extends Reducer<TextPair, IntWritable, TextPair, IntWritable> {
		private IntWritable result = new IntWritable();

		public void reduce(TextPair key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			int sum = 0;
			for (IntWritable val : values) {
				sum += val.get();
			}
			result.set(sum);
			context.write(key, result);
		}
	}

	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
		// TODO Auto-generated method stub
		
		Configuration conf = new Configuration();
		/*if (args.length < 2) {
			System.err.println("Usage: AddressSegmentTage <in> [<in>...] <out>");
			System.exit(2);
		}*/
		
		String[] otherArgs = {"test/input/inputfile", "test/output"};
        Job job = new Job(conf, "AddressSegmentTage");
        
        job.setJarByClass(AddressSegment.class); //设置运行jar中的class名称  
        
        job.setMapperClass(AddressMap.class);//设置mapreduce中的mapper reducer combiner类  
        job.setCombinerClass(AddressReduce.class); 
        job.setReducerClass(AddressReduce.class);  
          
          
        job.setOutputKeyClass(TextPair.class); //设置输出结果键值对类型
        job.setOutputValueClass(IntWritable.class); 
        
        FileInputFormat.addInputPaths(job, otherArgs[0]);//设置mapreduce输入输出文件路径
        FileOutputFormat.setOutputPath(job,new Path(otherArgs[1]));
        
        System.exit(job.waitForCompletion(true) ? 0 : 1);

	}

}
