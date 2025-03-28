package hk.ust.csit5970;

import org.apache.commons.cli.*;
import org.apache.commons.math3.analysis.function.Log;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import hk.ust.csit5970.CORStripes.CORStripesCombiner2;
import hk.ust.csit5970.CORStripes.CORStripesMapper2;
import hk.ust.csit5970.CORStripes.CORStripesReducer2;

import static org.mockito.Answers.values;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

/**
 * Compute the bigram count using "pairs" approach
 */
public class CORStripes extends Configured implements Tool {
	private static final Logger LOG = Logger.getLogger(CORStripes.class);
	/*
	 * TODO: write your first-pass Mapper here.
	 */
	private static class CORMapper1 extends
			Mapper<LongWritable, Text, Text, IntWritable> {
        private static final Text WORD=new Text();
        private static final IntWritable COUNT=new IntWritable();
		@Override
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			HashMap<String, Integer> word_set = new HashMap<String, Integer>();
			// Please use this tokenizer! DO NOT implement a tokenizer by yourself!
			String clean_doc = value.toString().replaceAll("[^a-z A-Z]", " ");
			StringTokenizer doc_tokenizer = new StringTokenizer(clean_doc);
			/*
			 * TODO: Your implementation goes here.
			 */
            word_set.clear();
            while(doc_tokenizer.hasMoreTokens())
            {
                String token=doc_tokenizer.nextToken();
                if(word_set.containsKey(token))
                    word_set.put(token,word_set.get(token)+1);
                else
                    word_set.put(token, 1);
            }
            for(Map.Entry<String,Integer> e:word_set.entrySet())
            {
                WORD.set(e.getKey());
                COUNT.set(e.getValue());
                context.write(WORD, COUNT);
            }
		}
	}

	/*
	 * TODO: Write your first-pass reducer here.
	 */
	private static class CORReducer1 extends
			Reducer<Text, IntWritable, Text, IntWritable> {
        private static final Text WORD=new Text();
        private static final IntWritable SUM=new IntWritable();
		@Override
		public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
			/*
			 * TODO: Your implementation goes here.
			 */
            int sum=0;
            for(IntWritable i:values)
                sum+=i.get();
            WORD.set(key);
            SUM.set(sum);
            context.write(WORD, SUM);
            
            /////////////////////////////////////
            // WORD.set(key+"M");
            // context.write(key, SUM);
		}
	}

	/*
	 * TODO: Write your second-pass Mapper here.
	 */
	public static class CORStripesMapper2 extends Mapper<LongWritable, Text, Text, MapWritable> {
        private static final Text KEY=new Text();
        private static final Text WORD=new Text();
        private static final MapWritable STRIPE=new MapWritable();
        private static final IntWritable ONE=new IntWritable(1);
		@Override
		protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			Set<String> sorted_word_set = new TreeSet<String>();
			// Please use this tokenizer! DO NOT implement a tokenizer by yourself!
			String doc_clean = value.toString().replaceAll("[^a-z A-Z]", " ");
			StringTokenizer doc_tokenizers = new StringTokenizer(doc_clean);
			while (doc_tokenizers.hasMoreTokens()) {
				sorted_word_set.add(doc_tokenizers.nextToken());
			}
			/*
			 * TODO: Your implementation goes here.
			 */
            ArrayList<String> wordList=new ArrayList<String>(sorted_word_set);
            // LOG.info(wordList.size());
            for(int i=0;i<wordList.size()-1;i++)
            {
                STRIPE.clear();
                KEY.set(wordList.get(i));
                for(int j=i+1;j<wordList.size();j++)
                {
                    STRIPE.put(new Text(wordList.get(j)), new IntWritable(1));
                    // LOG.info(wordList.get(i)+wordList.get(j));
                }
                context.write(KEY, STRIPE);
                // LOG.info(KEY.toString());
                // LOG.info(STRIPE.size());
                
                /////////////////////////////////////////
                // STRIPE.clear();
                // KEY.set(KEY.toString()+"M");
                // STRIPE.put(KEY, ONE);
                // context.write(KEY,STRIPE);
            }
		}
	}

	/*
	 * TODO: Write your second-pass Combiner here.
	 */
	public static class CORStripesCombiner2 extends Reducer<Text, MapWritable, Text, MapWritable> {
		static IntWritable ZERO = new IntWritable(0);

		@Override
		protected void reduce(Text key, Iterable<MapWritable> values, Context context) throws IOException, InterruptedException {
			/*
			 * TODO: Your implementation goes here.
			 */
            MapWritable SUM_STRIPE=new MapWritable();
            int count=0;
            for(MapWritable stripe:values)
            {
                for(Map.Entry<Writable,Writable> e:stripe.entrySet())
                {
                    Text k=(Text)e.getKey();
                    IntWritable v=(IntWritable)e.getValue();
                    if(!SUM_STRIPE.containsKey(k))
                        SUM_STRIPE.put(k, ZERO);
                    int sum=((IntWritable)(SUM_STRIPE.get(k))).get()+v.get();
                    SUM_STRIPE.put(k, new IntWritable(sum));
                }
                count+=stripe.size();
            }
            // LOG.info(key.toString());
            // LOG.info(count);

            context.write(key, SUM_STRIPE);
		}
	}

	/*
	 * TODO: Write your second-pass Reducer here.
	 */
	public static class CORStripesReducer2 extends Reducer<Text, MapWritable, PairOfStrings, DoubleWritable> {
		private static Map<String, Integer> word_total_map = new HashMap<String, Integer>();
		private static IntWritable ZERO = new IntWritable(0);

		/*
		 * Preload the middle result file.
		 * In the middle result file, each line contains a word and its frequency Freq(A), seperated by "\t"
		 */
		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Path middle_result_path = new Path("mid/part-r-00000");
			Configuration middle_conf = new Configuration();
			try {
				FileSystem fs = FileSystem.get(URI.create(middle_result_path.toString()), middle_conf);

				if (!fs.exists(middle_result_path)) {
					throw new IOException(middle_result_path.toString() + "not exist!");
				}

				FSDataInputStream in = fs.open(middle_result_path);
				InputStreamReader inStream = new InputStreamReader(in);
				BufferedReader reader = new BufferedReader(inStream);

				LOG.info("reading...");
				String line = reader.readLine();
				String[] line_terms;
				while (line != null) {
					line_terms = line.split("\t");
					word_total_map.put(line_terms[0], Integer.valueOf(line_terms[1]));
					LOG.info("read one line!");
					line = reader.readLine();
				}
				reader.close();
				LOG.info("finished！");
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

		/*
		 * TODO: Write your second-pass Reducer here.
		 */
		@Override
		protected void reduce(Text key, Iterable<MapWritable> values, Context context) throws IOException, InterruptedException {
			/*
			 * TODO: Your implementation goes here.
			 */
            MapWritable SUM_STRIPE=new MapWritable();
            for(MapWritable stripe:values)
            {
                for(Map.Entry<Writable,Writable> e:stripe.entrySet())
                {
                    if(!SUM_STRIPE.containsKey(e.getKey()))
                        SUM_STRIPE.put(e.getKey(), ZERO);
                    int sum=((IntWritable)(SUM_STRIPE.get(e.getKey()))).get()+((IntWritable)(e.getValue())).get();
                    SUM_STRIPE.put(e.getKey(), new IntWritable(sum));
                }
            }
            for(Map.Entry<Writable,Writable> e:SUM_STRIPE.entrySet())
            {
                PairOfStrings p=new PairOfStrings(key.toString(),e.getKey().toString());
                double freq_ab=(double)((IntWritable)e.getValue()).get();
                int freq_a=word_total_map.get(key.toString());
                int freq_b=word_total_map.get(e.getKey().toString());
                context.write(p, new DoubleWritable(freq_ab/freq_a/freq_b));
            }
		}
	}

	/**
	 * Creates an instance of this tool.
	 */
	public CORStripes() {
	}

	private static final String INPUT = "input";
	private static final String OUTPUT = "output";
	private static final String NUM_REDUCERS = "numReducers";

	/**
	 * Runs this tool.
	 */
	@SuppressWarnings({ "static-access" })
	public int run(String[] args) throws Exception {
		Options options = new Options();

		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("input path").create(INPUT));
		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("output path").create(OUTPUT));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("number of reducers").create(NUM_REDUCERS));

		CommandLine cmdline;
		CommandLineParser parser = new GnuParser();

		try {
			cmdline = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println("Error parsing command line: "
					+ exp.getMessage());
			return -1;
		}

		// Lack of arguments
		if (!cmdline.hasOption(INPUT) || !cmdline.hasOption(OUTPUT)) {
			System.out.println("args: " + Arrays.toString(args));
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp(this.getClass().getName(), options);
			ToolRunner.printGenericCommandUsage(System.out);
			return -1;
		}

		String inputPath = cmdline.getOptionValue(INPUT);
		String middlePath = "mid";
		String outputPath = cmdline.getOptionValue(OUTPUT);

		int reduceTasks = cmdline.hasOption(NUM_REDUCERS) ? Integer
				.parseInt(cmdline.getOptionValue(NUM_REDUCERS)) : 1;

		LOG.info("Tool: " + CORStripes.class.getSimpleName());
		LOG.info(" - input path: " + inputPath);
		LOG.info(" - middle path: " + middlePath);
		LOG.info(" - output path: " + outputPath);
		LOG.info(" - number of reducers: " + reduceTasks);

		// Setup for the first-pass MapReduce
		Configuration conf1 = new Configuration();

		Job job1 = Job.getInstance(conf1, "Firstpass");

		job1.setJarByClass(CORStripes.class);
		job1.setMapperClass(CORMapper1.class);
		job1.setReducerClass(CORReducer1.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(IntWritable.class);

		FileInputFormat.setInputPaths(job1, new Path(inputPath));
		FileOutputFormat.setOutputPath(job1, new Path(middlePath));

		// Delete the output directory if it exists already.
		Path middleDir = new Path(middlePath);
		FileSystem.get(conf1).delete(middleDir, true);

		// Time the program
		long startTime = System.currentTimeMillis();
		job1.waitForCompletion(true);
		LOG.info("Job 1 Finished in " + (System.currentTimeMillis() - startTime)
				/ 1000.0 + " seconds");

		// Setup for the second-pass MapReduce

		// Delete the output directory if it exists already.
		Path outputDir = new Path(outputPath);
		FileSystem.get(conf1).delete(outputDir, true);


		Configuration conf2 = new Configuration();
		Job job2 = Job.getInstance(conf2, "Secondpass");

		job2.setJarByClass(CORStripes.class);
		job2.setMapperClass(CORStripesMapper2.class);
		job2.setCombinerClass(CORStripesCombiner2.class);
		job2.setReducerClass(CORStripesReducer2.class);

		job2.setOutputKeyClass(PairOfStrings.class);
		job2.setOutputValueClass(DoubleWritable.class);
		job2.setMapOutputKeyClass(Text.class);
		job2.setMapOutputValueClass(MapWritable.class);
		job2.setNumReduceTasks(reduceTasks);

		FileInputFormat.setInputPaths(job2, new Path(inputPath));
		FileOutputFormat.setOutputPath(job2, new Path(outputPath));

		// Time the program
		startTime = System.currentTimeMillis();
		job2.waitForCompletion(true);
		LOG.info("Job 2 Finished in " + (System.currentTimeMillis() - startTime)
				/ 1000.0 + " seconds");

		return 0;
	}

	/**
	 * Dispatches command-line arguments to the tool via the {@code ToolRunner}.
	 */
	public static void main(String[] args) throws Exception {
		ToolRunner.run(new CORStripes(), args);
	}
}