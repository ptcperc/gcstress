/***************************************************************************
 * Copyright (c) 2020, PTC Inc. and/or all its affiliates.                 *
 * All rights reserved. See LICENSE file in the project root for           *
 * license information.                                                    *
 ***************************************************************************/

package gcstress.gc;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.text.SimpleDateFormat;

public class GCStress implements Runnable
{
  /**
   * Private static fields
   */
  private static int NUM_SAMPLES = 300;
  private static int SLEEP_TIME = 100;
  private static String OUTPUT_CSV = "gcstress.csv";
  private static long NANOS_PER_MILLI = 1000L * 1000L;
  private static double MICROS_PER_SECOND = 1000.0d * 1000.0d;
  private static String rt_name = System.getProperty("java.runtime.name");
  private static String rt_version = System.getProperty("java.runtime.version");
  private static String vm_name = System.getProperty("java.vm.name");
  private static String os_name = System.getProperty("os.name");
  private static String os_version = System.getProperty("os.version");
  private static String COMMA = ",";
  private static String QUOTE = "\"";
  private static String NEWLINE = "\n";
  private static String DATEFORMAT = "yyyy-MM-dd HH:mm:ss";

  // default cache capacity
  private static final int CACHE_CAP = 2000000;
  // default max cache entry size
  private static int CACHE_MAX_ENTRY_SIZE = 256;

  /**
   * Private instance fields
   */
  private long timestamp[];
  private long mem_kilos[];
  private long delay_micros[];
  private int sample_index = 0;

  // cache capacity
  private int capacity;

  // cache entry size;
  private int entry_size;

  // number of samples to take
  private int num_samples;

  // the output csv file name
  private String output_file;
 
  // sleep time for each sample in milliseconds
  private long sleepTime;

  // final stats
  private double std_dev;
  private double dmax;
  private double dmin;
  private double dmean;
  private double dmem_max;
  private double dmem_min;

  //************************************************************************
  //*                      PUBLIC CONSTRUCTORS                             *
  //************************************************************************

  public GCStress(long sleepTime, 
                  int num_samples, 
                  String output) 
  {
    this.sleepTime  = sleepTime;
    this.num_samples  = num_samples;
    this.output_file = output;
    timestamp = new long[num_samples];
    mem_kilos = new long[num_samples];
    delay_micros = new long[num_samples];

  }

  //************************************************************************
  //*                      PUBLIC STATIC METHODS                           *
  //************************************************************************

  public static void main(String[] args) throws Throwable
  {

    int opt_capacity = CACHE_CAP;
    int opt_maxsize = CACHE_MAX_ENTRY_SIZE;
    int opt_sleepTime = SLEEP_TIME;
    int opt_samples = NUM_SAMPLES;
    String opt_output = OUTPUT_CSV;

    // check command line args
    for (int i = 0; i < args.length; i++)
    {
      if (args[i].equals("-h") || args[i].equals("--help"))
      {
        usage();
        System.exit(0);
      }
      else if (args[i].startsWith("--capacity="))
      {
        try
        {
          opt_capacity = 
            Integer.parseInt(args[i].substring(args[i].indexOf('=') + 1));
        }
        catch (NumberFormatException nfe)
        {
          System.err.println("Bad capacity: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--maxsize="))
      {
        try
        {
          opt_maxsize = 
            Integer.parseInt(args[i].substring(args[i].indexOf('=') + 1));
        }
        catch (NumberFormatException nfe)
        {
          System.err.println("Bad maxsize: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--sleep="))
      {
        try
        {
          opt_sleepTime = 
            Integer.parseInt(args[i].substring(args[i].indexOf('=') + 1));
        }
        catch (NumberFormatException nfe)
        {
          System.err.println("Bad sleep time: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--samples="))
      {
        try
        {
          opt_samples = 
            Integer.parseInt(args[i].substring(args[i].indexOf('=') + 1));
        }
        catch (NumberFormatException nfe)
        {
          System.err.println("Bad samples: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--output="))
      {
        try
        {
          opt_output = 
            args[i].substring(args[i].indexOf('=') + 1);
        }
        catch (Exception e)
        {
          System.err.println("Bad output: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else
      {
        System.err.println("Unknown option: "+args[i]);
        usage();
        System.exit(-1);
      }
    }
    
    System.out.println("Starting GC Stress: samples="+opt_samples+" sleep="+
                       opt_sleepTime+" ms");

    // start thread to hammer heap memory at normal priority
    GCHammer hammer = new GCHammer(opt_capacity, opt_maxsize);
    Thread hammerThrd = new Thread(hammer, "GCStress Hammer");
    hammerThrd.setDaemon(true);
    hammerThrd.start();

    // boost ourselves to max priority
    int priority = Thread.MAX_PRIORITY;
    Thread.currentThread().setPriority(priority);

    // start the timer also at max priority
    GCStress gcstress = new GCStress(opt_sleepTime, 
                                     opt_samples, 
                                     opt_output);

    Thread timerThrd = new Thread(gcstress, "GCStress Timer");
    timerThrd.setPriority(priority);
    timerThrd.setDaemon(true);
    timerThrd.start();

    try
    {
      // wait for the timer to finish 
      timerThrd.join();
    }
    catch (InterruptedException ie)
    {
    }

    // stop the hammer
    hammer.stop();
    try
    {
      // wait for the hammer to finish
      hammerThrd.join();
    }
    catch (InterruptedException ie)
    {
    }
    finally
    {
      hammer = null;
      hammerThrd = null;
    }

    gcstress.displayResults();
    System.out.println("Generating "+opt_output+"...");
    gcstress.outputResults();
  }

  //************************************************************************
  //*                      PUBLIC INSTANCE METHODS                         *
  //************************************************************************

  public void run()
  {
    performTest();
  }

  //************************************************************************
  //*                      PRIVATE INSTANCE METHODS                        *
  //************************************************************************

  private static void usage()
  {
    System.out.println("Usage: GCStress <options>");
    System.out.println("  where <options> can be: (default)");
    System.out.println("  --help           print this message");
    System.out.println("  --capacity=<num> set cache capacity (2000000)");
    System.out.println("  --maxsize=<num>  set cache entry max size (256)");
    System.out.println("  --sleep=<num>    set sleep milliseconds (100)");
    System.out.println("  --samples=<num>  set number of samples (300)");
    System.out.println("  --output=<file>  set output csv file (gcstress.csv)");
  }

  private void initSampleData()
  {
    sample_index = 0;
    Arrays.fill(timestamp, 0L);
    Arrays.fill(mem_kilos, 0L);
    Arrays.fill(delay_micros, 0L);
  }

  private void addSample(long smpl)
  {
    timestamp[sample_index] = System.currentTimeMillis();
    Runtime rt = Runtime.getRuntime();
    mem_kilos[sample_index] = (rt.totalMemory() - rt.freeMemory()) / 1024;
    delay_micros[sample_index++] = smpl;
  }

  private void displayResults()
  {
    long delay_max = Long.MIN_VALUE;
    long delay_min = Long.MAX_VALUE;
    long delay_mean = 0L;
    long mem_max = Long.MIN_VALUE;
    long mem_min = Long.MAX_VALUE;
    for (int i = 0; i < sample_index; i++)
    {
      if (mem_kilos[i] > mem_max)
      {
        mem_max = mem_kilos[i];
      }
      if (mem_kilos[i] < mem_min)
      {
        mem_min = mem_kilos[i];
      }
      if (delay_micros[i] > delay_max)
      {
        delay_max = delay_micros[i];
      }
      if (delay_micros[i] < delay_min)
      {
        delay_min = delay_micros[i];
      }
      delay_mean += delay_micros[i];
    }
    delay_mean /= sample_index;

    double sum_squares = 0.0d;
    for (int i = 0; i < sample_index; i++)
    {
      double diff = (double)delay_micros[i] / MICROS_PER_SECOND
                             - (double)delay_mean / MICROS_PER_SECOND;
      sum_squares += diff * diff;
    }
    std_dev = Math.sqrt(sum_squares / (double)(sample_index - 1));
    dmax = (double)delay_max / MICROS_PER_SECOND;
    dmin = (double)delay_min / MICROS_PER_SECOND;
    dmean = (double)delay_mean / MICROS_PER_SECOND;
    dmem_max = (double)mem_max;
    dmem_min = (double)mem_min;

    System.out.format("JVM: %s%n", vm_name);
    System.out.format("Name: %s%n", rt_name);
    System.out.format("Version: %s%n", rt_version);
    System.out.format("OS: %s %s%n", os_name, os_version);
    System.out.format("Samples: %d%n", sample_index);
    System.out.format("Max memory: %.1f KBytes%n", dmem_max);
    System.out.format("Min memory: %.1f KBytes%n", dmem_min);
    System.out.format("Sleep time: %.6f seconds%n", 
                      (double)sleepTime / 1000.0d);
    System.out.format("Max delay:  %.6f seconds%n", dmax);
    System.out.format("Min delay:  %.6f seconds%n", dmin);
    System.out.format("Avg delay:  %.6f seconds%n", dmean);
    System.out.format("Std Dev:    %.6f seconds%n%n", std_dev);
  }

  private void outputResults()
  {
    Date date = new Date();
    SimpleDateFormat sdf = new SimpleDateFormat(DATEFORMAT);
    try
    {
      FileWriter outfw = new FileWriter(output_file);
      outfw.append(QUOTE + "Test=" + QUOTE + COMMA + 
                   QUOTE + "GC Stress" + QUOTE + NEWLINE);
      outfw.append(QUOTE + "VM=" + QUOTE + COMMA +
                   QUOTE + vm_name + QUOTE + NEWLINE);
      outfw.append(QUOTE + "Date=" + QUOTE + COMMA +
                   QUOTE + sdf.format(date) + QUOTE + NEWLINE);
      outfw.append(QUOTE + "Samples=" + QUOTE + COMMA + 
                   (num_samples) + NEWLINE);
      outfw.append(QUOTE + "Max Memory=" + QUOTE + COMMA + 
                   String.format("%.1f", dmem_max) + NEWLINE);
      outfw.append(QUOTE + "Min Memory=" + QUOTE + COMMA + 
                   String.format("%.1f", dmem_min) + NEWLINE);
      outfw.append(QUOTE + "Max Delay=" + QUOTE + COMMA + 
                   String.format("%.6f", dmax) + NEWLINE);
      outfw.append(QUOTE + "Min Delay=" + QUOTE + COMMA + 
                   String.format("%.6f", dmin) + NEWLINE);
      outfw.append(QUOTE + "Avg Delay=" + QUOTE + COMMA + 
                   String.format("%.6f", dmean) + NEWLINE);
      outfw.append(QUOTE + "Std Dev=" + QUOTE + COMMA + 
                   String.format("%.6f", std_dev) + NEWLINE);
      outfw.append(QUOTE + "Time" + QUOTE + COMMA +
                   QUOTE + "Used Memory" + QUOTE + COMMA +
                   QUOTE + "Delay" + QUOTE + NEWLINE);
      for (int i = 0; i < sample_index; i++)
      {
        outfw.append(timestamp[i] + COMMA +
                     (mem_kilos[i]) + COMMA +
                     delay_micros[i] + NEWLINE);
      }
      outfw.flush();
      outfw.close();
    }
    catch (Exception e)
    {
      System.err.println(e);
      System.exit(-1);
    }
  }

  //************************************************************************
  //*                      PROTECTED INSTANCE METHODS                      *
  //************************************************************************

  protected void performTest()
  {
    long sleep_time_ns = sleepTime * NANOS_PER_MILLI; 
    initSampleData();

    for (int count = 0; count < num_samples; count++)
    {
      long start = 0;
      long stop  = 0;

      try
      {
        start = System.nanoTime();
        Thread.sleep(sleepTime);
        stop = System.nanoTime();
      }
      catch (InterruptedException e)
      {
        System.err.println("Inconceivable!");
        System.exit(-1);
      }

      long elapsed = stop - start;
      long delay = elapsed - sleep_time_ns;

      addSample(delay / 1000);
    }
  }
}
 
  //************************************************************************
  //*                      PACKAGE CLASSES                                 *
  //************************************************************************

/**
 * A subclass of LinkedHashMap with a capacity
 */
class LinkedHashMapWithCapacity<K,V> extends LinkedHashMap<K,V> 
{
  private static final long serialVersionUID = 1L;

  private int capacity;

  public LinkedHashMapWithCapacity(int capacity) 
  {
    super(capacity, 0.75f, true);
    this.capacity = capacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K,V> eldest) 
  {
    return this.size() > this.capacity;
  }
}

/**
 * The GCHammer class mutates heap memory
 */
class GCHammer implements Runnable 
{
  private int capacity;
  private int maxsize;
  private LinkedHashMapWithCapacity<Integer,Object> map;
  private boolean stop = false;

  public GCHammer(int capacity, int maxsize) 
  {
    this.capacity = capacity;
    this.maxsize = maxsize;
    this.map = new LinkedHashMapWithCapacity<>(capacity);
  }

  public void stop()
  {
    stop = true;
  }

  public void run()
  {
    Random rand = new Random();
    while (!stop)
    {
      int key = rand.nextInt(capacity);
      int size = rand.nextInt(maxsize);
      byte[] val = (byte[])map.get(key);
      if (val == null)
      {
        // if the cache entry is empty, fill it
        map.put(key, new byte[size]);
      }
      else
      {
        // otherwise, remove it
        val = (byte[])map.remove(key);
      }
    }
  }
}
