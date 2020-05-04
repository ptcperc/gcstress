
# GCStress

The GCStress demo measures Java VM response time under garbage collector stress.

## License
The sources in this repo are licensed under terms of the MIT License. See:
![License](/LICENSE).


## How GCStress works

The GCStress program spawns two threads:

- The GCStress Timer thread executes at Thread.MAX_PRIORITY. It runs a 
  loop that samples the current nanotime, sleeps for a specified time 
  (default 100 ms), and then checks nanotime again. The difference between 
  expected sleep time and actual sleep time in nanoseconds is calculated. 
  A timestamp, the current used heap memory in KB, and the delay are 
  recorded. By default, the Timer takes 300 samples and then exits, for a 
  total of about 30 seconds (300 * 0.1 sec).
- The GCStress Hammer thread executes at Thread.NORM_PRIORITY. It creates 
  a LinkedHashMap as a cache with a default capacity of 2,000,000 objects 
  keyed by an Integer. The Hammer thread runs a loop that generates 
  a random Integer and checks the cache to see if an entry for that key
  is present. If so, the cache entry is removed. Otherwise a new byte array 
  of random size 0-255 bytes is allocated and added to the cache for the key. 
  Over time this causes fragmentation of Java heap memory as objects are added 
  and removed from the LinkedHashMap.

After the Timer thread completes, GCStress computes statistics from the 
samples and outputs VM information, today's date/time, sample statistics, 
and the raw sample data to a CSV formatted file.

A separate GCDelayGraph program takes the CSV output file and generates a 
JPG chart showing used heap memory as a red line graph overlayed with the 
timer delays as a blue bar graph. The delay graph uses a logarithmic scale 
for visibility of smaller delay values.

## System Requirements

GCStress is a Java application. It requires a Java 8 or later JVM. It has 
been tested with OpenJDK 8, Oracle Java 8, and the PTC Perc 8.3 real-time 
JVM. For this README, we will run GCStress on a Linux/x86_64 host with 
OpenJDK 8 installed. We will also show you how to install and run the demo
in a Perc 8.3 VM binary that has the GCStress classes AOT-compiled in a 
standalone executable.

## Installing GCStress

To install GCStress, click on the releases icon above the gcstress
repo file list, select the latest release and download the 
gcstress-\<version\>.tar.gz file from the Assets list. Then extract 
the archive to create the gcstress-\<version\> directory.

## Running GCStress with Java

The GCStress install directory includes the ./lib/gcstress.jar file. Here is
an example Java command line that runs the demo: 

```console
$ cd gcstress-<version>
$ java -Xmx400m -jar ./lib/gcstress.jar
Starting GC Stress: samples=300 sleep=100 ms
JVM: OpenJDK 64-Bit Server VM
Name: OpenJDK Runtime Environment
Version: 1.8.0_172-internal-b11
OS: Linux 3.10.0-693.11.6.el7.x86_64
Samples: 300
Max memory: 309429.0 KBytes
Min memory: 40960.0 KBytes
Sleep time: 0.100000 seconds
Max delay:  0.652522 seconds
Min delay:  0.000090 seconds
Avg delay:  0.148908 seconds
Std Dev:    0.232969 seconds

Generating gcstress.csv...
```

The 'java -Xmx400m' option sets the maximum Java heap size so the JVM is 
forced to do garbage collection when the heap would otherwise exceed 
400 MBytes. 


The "Max delay" value for OpenJDK 8 is 652 milliseconds and the average is 
about 149 milliseconds.

Now you can use ./lib/gcgraph.jar to generate a graph of memory usage and
delays from the generated out.csv:

```console
$ java -jar ./lib/gcgraph.jar
Creating GC Stress Graph: input=gcstress.csv chart=gcstress.jpg width=600 height=400
```
Then view gcstress.jpg on your desktop using your favorite viewer:

![Java Chart](/images/java1.jpg)

In addition to the delays, you can see the pattern of heap memory usage in red. 
It climbs rapidly to 300+ MBytes and then drops to 200 MBytes when the JVM does 
a garbage collection. All Java threads must pause while the garbage collector 
moves objects in heap memory and updates pointers to the new locations.

Note that after each drop in memory usage you see a long delay recorded 
by the high-priority timer thread. The timer was prevented from running 
while the garbage collector paused all Java threads.

Here is the GCStress help message:

```console
$ java -jar ./lib/gcstress.jar --help
Usage: GCStress <options>
  where <options> can be: (default)
    --help           print this message
    --capacity=<num> set cache capacity (2000000)
    --maxsize=<num>  set cache entry max size (256)
    --sleep=<num>    set sleep milliseconds (100)
    --samples=<num>  set number of samples (300)
    --output=<file>  set output csv file (gcstress.csv)
```
You can set the capacity of the cache, the maximum size of a cache entry,
the sleep time of the timer thread, the number of samples to take, and the 
name of the CSV output file. Note that if you increase capacity or maxsize,
you may need to increase the max heap memory on the Java command line.

And the GCDelayGraph help message:
```console
$ java -jar ./lib/gcgraph.jar --help
Usage: GCDelayGraph <options>
  where <options> can be: (default)
    --help           print this message
    --input=<file>   set input csv file (gcstress.csv)
    --chart=<file>   set output chart file (gcstress.jpg)
    --width=<num>    set chart width (600)
    --height=<num>   set chart height (400)
```
You can set the name of the CSV input file, the name of the JPG output file,
and the width and height of the chart in pixels.

## Running GCStress with PTC Perc Real-Time JVM (separately licensed)

To run the GCStress demo with PTC Perc, you will need to install
the pre-built pvm-gcstress binary (requires Linux/x86_64 host). You can
download the installer by clicking on the releases icon above the files
listed in the gcstress repo page and clicking on the latest
release. Then download the perc-gcstress-x64.bin file in the Assets list.
Then run:

```console
$ ./perc-gcstress-x64.bin
```
The installer displays the PTC Perc license agreement and asks you to
agree to its terms. If you agree, type "YES." Otherwise, type "NO."
If you answered "YES," the installer will create a ./bin directory
containing the license.txt file and the pvm-gcstress executable.

Now you can run the pre-compiled GCStress demo inside pvm-gcstress:

```console
$ ./bin/pvm-gcstress --heap-region-count=400 --heap-expand-rate=0 gcstress.gc.GCStress
Evaluation PVM will exit after 1 hour
Starting GC Stress: samples=300 sleep=100 ms
JVM: PTC Perc(R) 64 VM
Name: PTC Perc(R) 64 Runtime Environment
Version: 1.8.0_212-b04
OS: Linux 3.10.0-693.11.6.el7.x86_64
Samples: 300
Max memory: 411739.0 KBytes
Min memory: 27279.0 KBytes
Sleep time: 0.100000 seconds
Max delay:  0.000187 seconds
Min delay:  -0.000448 seconds
Avg delay:  -0.000007 seconds
Std Dev:    0.000082 seconds

Generating gcstress.csv...
```
Note the pvm-gcstress command-line options are setting the heap region 
count to 400 regions (1 MB each) and disabling heap expansion. This gives 
Perc the same heap memory as Java.

Now you can use java to generate a chart of the Perc results:

```console
$ java -jar ./lib/gcgraph.jar
Creating GC Stress Graph: input=gcstress.csv chart=gcstress.jpg width=600 height=400
```

And view the resulting graph:

![Perc Chart](/images/perc1.jpg)

Here you can see the difference a real-time JVM makes. Heap usage 
increases steadily until near maximum and the garbage collector paces
itself to the allocation rate of the GCStress application. PTC Perc has a
187 microsecond maximum delay compared to 652 milliseconds for OpenJDK. This is
because the Perc real-time garbage collector is preemptible by
high-priority threads even while it is defragmenting the heap. There are
no "stop-the-world" pauses.

If your results with Perc show Max delays larger than 1 millisecond, it is
likely that the Linux kernel or other processes are interfering with Perc.
You can avoid this by running the VM in a real-time scheduling policy. Real-
time Linux processes have priority over all normal processes in the system.

For this you may need additional privileges for your account. Run this command
to see if you can set real-time priorities:
```console
$ ulimit -r
```
If the command prints '0' you will need to work with your system administrator 
to give 'rtprio 99' privilege to your account. They can do this by adding
a line to /etc/security/limits.conf:

```console
username    -    rtprio    99
```
Then logout/login with your username and try 'ulimit -r' again. If it says
'99' you are ready to use real-time priorities.

Here is a new pvm-gcstress command that sets real-time policy to "rr"
(round robin) and assigns the top Java thread priority to Linux 
priority 48 (the range is 1 - 99):

```console
$ ./bin/pvm-gcstress --prio=rr --priority-levels=48 --heap-region-count=400 --heap-expand-rate=0 gcstress.gc.GCStress
```

The Max delay should be below 1 millisecond.

### Interested in evaluating Perc?

No problem. For a time-limited evaluation of Perc, go to:
https://www.ptc.com/en/products/developer-tools/perc 
and click on the "Contact Us" button to request an eval. An account
representative will get in touch with you to set it up. While you're there,
click on "Using Real-Time Java for Industrial Control" to watch a video
of Perc responding to a light sensor fast enough to sort ping-pong 
balls by color. 

## Building GCStress

The source code and an Ant build.xml for GCStress are provided for
your convenience to modify and experiment as you wish. You will need 
Apache Ant 1.8 or higher and a JDK 1.8 or higher. To build, simply run 
'ant' in the gcstress-\<version\> directory:
```console
$ cd gcstress-<version>
$ ant
```

The resulting classes are placed in the build/classes directory and the
gcstress.jar and gcgraph.jar files are placed in the build/lib directory. 
You can build your own gcstress-\<version\>tar.gz release archive with this 
command:
```console
$ ant release
```

Enjoy!


Copyright (C) 2020 PTC Inc. All rights reserved. PTC and Perc are registered 
trademarks of PTC Inc. Java is a trademark of Oracle Corporation. 
All other trademarks are trademarks or registered trademarks of their 
respective owners.
