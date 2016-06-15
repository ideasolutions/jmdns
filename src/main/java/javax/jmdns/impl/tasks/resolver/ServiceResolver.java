// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package javax.jmdns.impl.tasks.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.DNSOutgoing;
import javax.jmdns.impl.DNSQuestion;
import javax.jmdns.impl.DNSRecord;
import javax.jmdns.impl.JmDNSImpl;
import javax.jmdns.impl.constants.DNSConstants;
import javax.jmdns.impl.constants.DNSRecordClass;
import javax.jmdns.impl.constants.DNSRecordType;

/**
 * The ServiceResolver queries three times consecutively for services of a given type, and then removes itself from the timer.
 * <p/>
 * The ServiceResolver will run only if JmDNS is in state ANNOUNCED. REMIND: Prevent having multiple service resolvers for the same type in the timer queue.
 */
public class ServiceResolver extends DNSResolverTask {
    private static Logger logger = LoggerFactory.getLogger(ServiceResolver.class.getName());

    private final String _type;

    private Timer _timer;

    public ServiceResolver(JmDNSImpl jmDNSImpl, String type) {
        super(jmDNSImpl);
        this._type = type;
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.DNSTask#getName()
     */
    @Override
    public String getName() {
        return "ServiceResolver(" + (this.getDns() != null ? this.getDns().getName() : "") + ")";
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#addAnswers(javax.jmdns.impl.DNSOutgoing)
     */
    @Override
    protected DNSOutgoing addAnswers(DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        long now = System.currentTimeMillis();
        for (ServiceInfo info : this.getDns().getServices().values()) {
            newOut = this.addAnswer(newOut, new DNSRecord.Pointer(info.getType(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, info.getQualifiedName()), now);
            // newOut = this.addAnswer(newOut, new DNSRecord.Service(info.getQualifiedName(), DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE, DNSConstants.DNS_TTL, info.getPriority(), info.getWeight(), info.getPort(),
            // this.getDns().getLocalHost().getName()), now);
        }
        return newOut;
    }

    @Override
    public void start(Timer timer) {
        if (!this.getDns().isCanceling() && !this.getDns().isCanceled()) {
            _timer = timer;
            timer.schedule(this, randInt(20, 120));
        }
    }

    @Override
    public void run() {
        super.run();
        if (_timer != null &&
                !this.getDns().isCanceling() && !this.getDns().isCanceled() &&
                _count < getExecutionCount()) {
            int delay = (int) (Math.pow(2, (_count - 1)) * 1000);
            _timer.schedule(new TimerTaskWrapper(this), delay);
        } else {
            _timer = null;
        }
    }

    // avoid  java.lang.IllegalStateException: TimerTask is scheduled already
    static class TimerTaskWrapper extends TimerTask {
        final TimerTask task;
        TimerTaskWrapper(TimerTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }

    @Override
    protected int getExecutionCount() {
        return 5;
    }

    /**
     * Returns a pseudo-random number between min and max, inclusive.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimum value
     * @param max Maximum value.  Must be greater than min.
     * @return Integer between min and max, inclusive.
     * @see java.util.Random#nextInt(int)
     */
    public static int randInt(int min, int max) {

        Random rand = new Random();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }

    /*
         * (non-Javadoc)
         * @see javax.jmdns.impl.tasks.Resolver#addQuestions(javax.jmdns.impl.DNSOutgoing)
         */
    @Override
    protected DNSOutgoing addQuestions(DNSOutgoing out) throws IOException {
        DNSOutgoing newOut = out;
        newOut = this.addQuestion(newOut, DNSQuestion.newQuestion(_type, DNSRecordType.TYPE_PTR, DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE));
        // newOut = this.addQuestion(newOut, DNSQuestion.newQuestion(_type, DNSRecordType.TYPE_SRV, DNSRecordClass.CLASS_IN, DNSRecordClass.NOT_UNIQUE));
        return newOut;
    }

    /*
     * (non-Javadoc)
     * @see javax.jmdns.impl.tasks.Resolver#description()
     */
    @Override
    protected String description() {
        return "querying service";
    }
}