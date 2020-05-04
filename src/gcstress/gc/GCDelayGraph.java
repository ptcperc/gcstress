/***************************************************************************
 * Copyright (c) 2020, PTC Inc. and/or all its affiliates.                 *
 * All rights reserved. See LICENSE file in the project root for           *
 * license information.                                                    *
 ***************************************************************************/

package gcstress.gc;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.awt.Font;
import java.awt.Color;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.VerticalAlignment;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.XYDataset;

public class GCDelayGraph 
{
  /**
   * Private static fields
   */
  private static String INPUT_CSV = "gcstress.csv";
  private static String CHART_FILE = "gcstress.jpg";
  private static int WIDTH = 600;
  private static int HEIGHT = 400;

  //************************************************************************
  //*                      PUBLIC STATIC METHODS                           *
  //************************************************************************

  public static void main(String[] args)
  {
    String opt_input = INPUT_CSV;
    String opt_chart = CHART_FILE;
    int opt_width = WIDTH;
    int opt_height = HEIGHT;

    // check command line args
    for (int i = 0; i < args.length; i++)
    {
      if (args[i].equals("-h") || args[i].equals("--help"))
      {
        usage();
        System.exit(0);
      }
      if (args[i].startsWith("--input="))
      {
        try
        {
          opt_input = 
            args[i].substring(args[i].indexOf('=') + 1);
        }
        catch (Exception e)
        {
          System.err.println("Bad input: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--chart="))
      {
        try
        {
          opt_chart = 
            args[i].substring(args[i].indexOf('=') + 1);
        }
        catch (Exception e)
        {
          System.err.println("Bad chart: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--width="))
      {
        try
        {
          opt_width = 
            Integer.parseInt(args[i].substring(args[i].indexOf('=') + 1));
        }
        catch (NumberFormatException nfe)
        {
          System.err.println("Bad width: "+args[i]);
          usage();
          System.exit(-1);
        }
      }
      else if (args[i].startsWith("--height="))
      {
        try
        {
          opt_height = 
            Integer.parseInt(args[i].substring(args[i].indexOf('=') + 1));
        }
        catch (NumberFormatException nfe)
        {
          System.err.println("Bad height: "+args[i]);
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

    System.out.println("Creating GC Stress Graph: input="+opt_input+" chart="+
                       opt_chart+" width="+opt_width+" height="+opt_height);
    // generate the chart
    chartFromCSV(opt_input, opt_chart, opt_width, opt_height);
  }

  /**
   * generate a jpeg chart of memory usage vs sleep delay from a CSV input file
   */
  public static void chartFromCSV(String input_file,
                                  String output_file,
                                  int width,
                                  int height) 
  {
    // avoid using X11 display
    System.setProperty("java.awt.headless", "true");
    CSVData csvd = parseCSV(input_file);
    JFreeChart chart = createChart(csvd);
    try
    {
      ChartUtils.saveChartAsJPEG(new File(output_file), chart, width, height);
    }
    catch (Exception e)
    {
      System.err.println(e);
      System.exit(-1);
    }
  }

  //************************************************************************
  //*                      PRIVATE STATIC METHODS                          *
  //************************************************************************

  /**
   * parse the CSV input file
   */
  private static CSVData parseCSV(String file)
  {
    String line;

    File csvFile = new File(file);
    if (!csvFile.exists() || !csvFile.isFile() || !csvFile.canRead())
    {
      System.err.println("Cannot read "+file);
      System.exit(-1);
    }

    CSVData csvd = new CSVData();
    
    try
    {
      BufferedReader br = new BufferedReader(new FileReader(csvFile));
      while ((line = br.readLine()) != null)
      {
        String[] vals = line.split(",");
        if (vals[0].indexOf('=') != -1 && vals.length == 2)
        {
          // add name=value to map
          csvd.putKeyValue(vals[0].replace("\"", "").replace("=", ""), 
                           vals[1].replace("\"", ""));
        }
        else if (vals.length == 3)
        {
          // must be labels or data
          if (vals[0].indexOf('"') != -1)
          {
            // labels
            csvd.setMemlabel(vals[1].replace("\"", ""));
            csvd.setDelaylabel(vals[2].replace("\"", ""));
          }
          else
          {
            // data
            csvd.addSample(new CSVSample(Long.parseLong(vals[0]),
                                         Integer.parseInt(vals[1]),
                                         Integer.parseInt(vals[2])));
          }
        }
        else
        {
          System.err.println("Read error: "+line);
          System.exit(-1);
        }
      }
      br.close();
    }                     
    catch (Exception e)
    {
      System.err.println("Read error: "+e);
      e.printStackTrace();
      System.exit(-1);
    }
    return csvd;
  }

  private static void usage()
  {
    System.out.println("Usage: GCDelayGraph <options>");
    System.out.println("  where <options> can be: (default)");
    System.out.println("  --help           print this message");
    System.out.println("  --input=<file>   set input csv file (gcstress.csv)");
    System.out.println("  --chart=<file>   set output chart file (gcstress.jpg)");
    System.out.println("  --width=<num>    set chart width (600)");
    System.out.println("  --height=<num>   set chart height (400)");
  }

  /**
   * create the chart from the parsed CSV data
   */
  private static JFreeChart createChart(CSVData csvd) 
  {
    XYDataset memData = createMemDataset(csvd);
    JFreeChart chart = ChartFactory.createTimeSeriesChart(
      null,
      "Seconds",
      "MBytes",
      memData,
      true,
      true,
      false
    );
    // set title
    String title = csvd.getValue("Test") + ": "+ csvd.getValue("VM");
    TextTitle tt = new TextTitle(title, 
                                 new Font("SansSerif", Font.BOLD, 18));
    chart.setTitle(tt);
    String subtitle = csvd.getValue("Date");
    TextTitle st = new TextTitle(subtitle,
                                 new Font("SansSerif", Font.BOLD, 16));
    chart.addSubtitle(st);
    XYPlot plot = (XYPlot) chart.getPlot();
    double mem_max = Double.parseDouble(csvd.getValue("Max Memory")) / 1024;
    double delay_max = Double.parseDouble(csvd.getValue("Max Delay"));
    double delay_min = Double.parseDouble(csvd.getValue("Min Delay"));

    String summary = 
      "Samples: " + csvd.getSize() + 
      "  MaxMemory: " + String.format("%.1f", mem_max) + " MB" +
      "  MaxDelay: " + String.format("%.6f", delay_max) + " sec" +
      "  MinDelay: " + String.format("%.6f", delay_min) + " sec";

    TextTitle lt = new TextTitle(summary,
                                 new Font("SansSerif", Font.PLAIN, 12),
                                 Color.black,
                                 RectangleEdge.BOTTOM,
                                 HorizontalAlignment.CENTER,
                                 VerticalAlignment.BOTTOM,
                                 RectangleInsets.ZERO_INSETS);
    chart.addSubtitle(0, lt);
    chart.getLegend().setFrame(new BlockBorder(1.0d, 1.0d, 1.0d, 1.0d));
    NumberAxis rangeAxis1 = (NumberAxis) plot.getRangeAxis();
    rangeAxis1.setLowerMargin(0.40);  // to leave room for delay bars
    DecimalFormat format1 = new DecimalFormat("##0.0");
    rangeAxis1.setNumberFormatOverride(format1);
    DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
    domainAxis.setDateFormatOverride(new SimpleDateFormat("ss.SSS"));
    domainAxis.setVerticalTickLabels(true);

    LogarithmicAxis rangeAxis2 = new LogarithmicAxis("Seconds");
    rangeAxis2.setRange(0.000001d, 1.0d);
    DecimalFormat format2 = new DecimalFormat("0.0");
    rangeAxis2.setNumberFormatOverride(format2);
    plot.setRangeAxis(1, rangeAxis2);
    plot.setDataset(1, createDelayDataset(csvd));
    plot.mapDatasetToRangeAxis(1, 1);
    XYBarRenderer renderer2 = new XYBarRenderer(0.20);
    plot.setRenderer(1, renderer2);
    renderer2.setBarPainter(new StandardXYBarPainter());
    renderer2.setShadowVisible(false);
    return chart;
  }

  /**
   * create the memory usage data set from the CSV data
   */
  private static XYDataset createMemDataset(CSVData csvd) 
  {
    TimeSeries series1 = new TimeSeries(csvd.getMemlabel());
    int size = csvd.getSize();
    for (int i = 0; i < size; i++)
    {
      CSVSample sample = csvd.getSample(i);
      series1.add(new FixedMillisecond(sample.timestamp), 
                  (double)sample.mem_kilos / 1000.0d);
    }
    return new TimeSeriesCollection(series1);
  }

  /**
   *  create the sleep delay data set from the CSV data
   */
  private static IntervalXYDataset createDelayDataset(CSVData csvd) 
  {
    TimeSeries series1 = new TimeSeries(csvd.getDelaylabel());
    int size = csvd.getSize();
    for (int i = 0; i < size; i++)
    {
      CSVSample sample = csvd.getSample(i);
      series1.add(new FixedMillisecond(sample.timestamp), 
                  (double)sample.delay_micros / 1000000.0d);
    }
    return new TimeSeriesCollection(series1);
  }

}

//************************************************************************
//*                      PACKAGE CLASSES                                 *
//************************************************************************

class CSVSample
{
  long timestamp;
  int mem_kilos;
  int delay_micros;

  CSVSample(long time, int mem, int delay)
  {
    timestamp = time;
    mem_kilos = mem;
    delay_micros = delay;
  }
}

class CSVData
{
  String memlabel = "";
  String delaylabel = "";
  Vector<CSVSample> samples = new Vector<CSVSample>();
  Map<String,String> key_values = new HashMap<String, String>();

  void putKeyValue(String key, String value)
  {
    key_values.put(key, value);
  }

  String getValue(String key)
  {
    String value = key_values.get(key);
    if (value == null)
    {
      value = "";
    }
    return value;
  }

  int getSize()
  {
    return samples.size();
  }

  void addSample(CSVSample sample)
  {
    samples.add(sample);
  }

  CSVSample getSample(int index)
  {
    return samples.elementAt(index);
  }

  void setMemlabel(String label)
  {
    this.memlabel = label;
  }

  String getMemlabel()
  {
    return memlabel;
  }

  void setDelaylabel(String label)
  {
    this.delaylabel = label;
  }

  String getDelaylabel()
  {
    return delaylabel;
  }

}

