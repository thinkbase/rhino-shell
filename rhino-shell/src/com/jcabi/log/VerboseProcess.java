/**
 * Copyright (c) 2012-2013, JCabi.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the jcabi.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jcabi.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Utility class for getting {@code stdout} from a running process
 * and logging it through SLF4J.
 *
 * <p>For example:
 *
 * <pre> String name = new VerboseProcess(
 *   new ProcessBuilder("who", "am", "i")
 * ).stdout();</pre>
 *
 * <p>The class throws an exception if the process returns a non-zero exit
 * code.
 *
 * <p>The class is thread-safe.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.5
 */
public final class VerboseProcess {
	private static final Logger log = Logger.getLogger(VerboseProcess.class);
	private static final Logger consoleLog = Logger.getLogger("_console_");
	
	/** The capacity of stdout buffer */
	private static final int CAPACITY_STDOUT = 1024;
	/** The capacity of stder buffer */
	private static final int CAPACITY_STDERR = 0;

    /**
     * The process we're working with.
     */
    private final transient Process process;

    /**
     * Log level for stdout.
     */
    private final transient Level out;

    /**
     * Log level for stderr.
     */
    private final transient Level err;

    /**
     * Public ctor.
     * @param prc The process to work with
     */
    public VerboseProcess(final Process prc) {
        this(prc, Level.INFO, Level.WARN);
    }

    /**
     * Public ctor (builder will be configured to redirect error stream to
     * the {@code stdout} and will receive an empty {@code stdin}).
     * @param builder Process builder to work with
     */
    public VerboseProcess(final ProcessBuilder builder) {
        this(VerboseProcess.start(builder));
    }

    /**
     * Public ctor, with a given process and logging levels for {@code stdout}
     * and {@code stderr}.
     * @param prc Process to execute and monitor
     * @param stdout Log level for stdout
     * @param stderr Log level for stderr
     * @since 0.11
     */
    public VerboseProcess(final Process prc, final Level stdout, final Level stderr) {
    	assert(null!=prc):"process can't be NULL";
    	assert(null!=stdout):"stdout level can't be NULL";
    	assert(null!=stderr):"stderr level can't be NULL";
    	
        this.process = prc;
        this.out = stdout;
        this.err = stderr;
    }

    /**
     * Public ctor, with a given process and logging levels for {@code stdout}
     * and {@code stderr}.
     * @param bdr Process builder to execute and monitor
     * @param stdout Log level for stdout
     * @param stderr Log level for stderr
     * @since 0.12
     */
    public VerboseProcess(final ProcessBuilder bdr, final Level stdout, final Level stderr) {
        this(VerboseProcess.start(bdr), stdout, stderr);
    }

    /**
     * Get {@code stdout}/{@code stderr} from the process, after its finish (the method will
     * wait for the process and log its output).
     *
     * <p>The method will check process exit code, and if it won't be equal
     * to zero a runtime exception will be thrown. A non-zero exit code
     * usually is an indicator of problem. If you want to ignore this code,
     * use {@link #stdoutQuietly()} instead.
     *
     * @return Full {@code stdout} of the process
     */
    public StringBuffer[] stdout() {
        return this.stdout(true);
    }

    /**
     * Get {@code stdout}/{@code stderr} from the process, after its finish (the method will
     * wait for the process and log its output).
     *
     * <p>This method ignores exit code of the process. Even if it is
     * not equal to zero (which usually is an indicator of an error), the
     * method will quietly return its output. The method is useful when
     * you're running a background process. You will kill it with
     * {@link Process#destroy()}, which usually will lead to a non-zero
     * exit code, which you want to ignore.
     *
     * @return Full {@code stdout} of the process
     * @since 0.10
     */
    public StringBuffer[] stdoutQuietly() {
        return this.stdout(false);
    }

    /**
     * Start a process from the given builder.
     * @param builder Process builder to work with
     * @return Process started
     */
    private static Process start(final ProcessBuilder builder) {
    	assert(null!=builder):"ProcessBuilder can't be NULL";
        //builder.redirectErrorStream(true);
        try {
            final Process process = builder.start();
            process.getOutputStream().close();
            return process;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Get standard output/error and check for non-zero exit code (if required).
     * @param check TRUE if we should check for non-zero exit code
     * @return Full {@code stdout} of the process
     */
    private StringBuffer[] stdout(final boolean check) {
        final long start = System.currentTimeMillis();
        final StringBuffer[] stdout;
        try {
            stdout = this.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        final int code = this.process.exitValue();
        log.debug(
            "#stdout(): process "+this.process+" completed (code="+code+")" +
            " in "+((System.currentTimeMillis() - start)/1000)+"s"
        );
        if (check && code != 0) {
            throw new IllegalArgumentException("Non-zero exit code "+code+": "+stdout);
        }
        return stdout;
    }

    /**
     * Wait for the process to stop, logging its output in parallel.
     * @return Stdout/Stderr produced by the process
     * @throws InterruptedException If interrupted in between
     */
    private StringBuffer[] waitFor() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(2);
        final StringBuffer stdout = new StringBuffer();
        final StringBuffer stderr = new StringBuffer();
        log.debug(
            "#waitFor(): waiting for stdout of "+this.process+" in "+
    		this.monitor(
                    this.process.getInputStream(),
                    done, stdout, CAPACITY_STDOUT, this.out
            )+"..."
        );
        log.debug(
            "#waitFor(): waiting for stderr of "+this.process+" in "+
            this.monitor(
                this.process.getErrorStream(),
                done, stderr, CAPACITY_STDERR, this.err
            )+"..."
        );
        try {
            this.process.waitFor();
        } finally {
            log.debug("#waitFor(): process "+this.process+" finished");
            done.await(2L, TimeUnit.SECONDS);
        }
        return new StringBuffer[]{stdout, stderr};
    }

    /**
     * Monitor this input stream.
     * @param stream Stream to monitor
     * @param done Count down latch to signal when done
     * @param buffer Buffer to write to
     * @param bufferSize The buffer size, zero means no limit
     * @param level Logging level
     * @return Thread which is monitoring
     * @checkstyle ParameterNumber (5 lines)
     */
    private Thread monitor(final InputStream stream, final CountDownLatch done,
        final StringBuffer buffer, final int bufferSize, final Level level) {
        final Thread thread = new Thread(
            new VerboseRunnable(
                // @checkstyle AnonInnerLength (100 lines)
                new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        final BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                stream,
                                Charset.forName(System.getProperty("file.encoding"))
                            )
                        );
                        try {
                            while (true) {
                                final String line = reader.readLine();
                                if (line == null) {
                                    break;
                                }
                                consoleLog.log(
                                    level, line
                                );
                                int len = buffer.length();
                                if (bufferSize>0 && len>bufferSize){
                                	buffer.delete(0, len-bufferSize);
                                }
                                buffer.append(line).append("\n");
                            }
                            done.countDown();
                        } finally {
                            try {
                                reader.close();
                            } catch (IOException ex) {
                                log.error(
                                    "failed to close reader: "+ex.getMessage()
                                );
                            }
                        }
                        return null;
                    }
                },
                false
            )
        );
        thread.setName("VerboseProcess");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

}
