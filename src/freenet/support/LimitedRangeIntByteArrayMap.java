package freenet.support;

import java.util.HashMap;
import java.util.Iterator;

import freenet.node.AsyncMessageCallback;

/**
 * @author amphibian
 * 
 * A list of integers linked to byte[]'s.
 * We keep a minimum and maximum of the integers.
 * The integers are expected to be reasonably close together, and
 * there is a maximum range. We provide a mechanism to sleep 
 * until the given packet number will no longer exceed the 
 * maximum range (which can be interrupted).
 */
public class LimitedRangeIntByteArrayMap {

    private final HashMap contents;
    private int minValue;
    private int maxValue;
    private final int maxRange;
    /** If this changes, all waiting lock()s must terminate */
    private volatile boolean flag;
    
    public LimitedRangeIntByteArrayMap(int maxRange) {
        this.maxRange = maxRange;
        contents = new HashMap();
        minValue = -1;
        maxValue = -1;
        flag = false;
    }
    
    public int minValue() {
        return minValue;
    }
    
    public int maxValue() {
        return maxValue;
    }
    
    public synchronized byte[] get(int index) {
        Integer i = new Integer(index);
        LimitedRangeIntByteArrayMapElement wrapper = (LimitedRangeIntByteArrayMapElement) contents.get(i);
        if(wrapper != null)
            return wrapper.data;
        else return null;
    }
    
    public synchronized AsyncMessageCallback[] getCallbacks(int index) {
        Integer i = new Integer(index);
        LimitedRangeIntByteArrayMapElement wrapper = (LimitedRangeIntByteArrayMapElement) contents.get(i);
        if(wrapper != null)
            return wrapper.callbacks;
        else return null;
    }
    
    /**
     * Try to add an index/data mapping.
     * @return True if we succeeded, false if the index was out
     * of range.
     */
    public synchronized boolean add(int index, byte[] data, AsyncMessageCallback[] callbacks) {
        Logger.minor(this, toString()+" add "+index);
        if(maxValue == -1) {
            minValue = index;
            maxValue = index;
        }
        if(index > maxValue) {
            if(index-minValue >= maxRange)
                return false;
            maxValue = index;
        }
        if(index < minValue) {
            if(maxValue-index >= maxRange)
                return false;
            minValue = index;
        }
        contents.put(new Integer(index), new LimitedRangeIntByteArrayMapElement(index, data, callbacks));
        notifyAll();
        return true;
    }
    
    /**
     * Toggle the flag, and notify all waiting lock()s. They will then throw
     * InterruptedException's.
     */
    public synchronized void interrupt() {
        flag = !flag;
        notifyAll();
    }

    /**
     * Wait until add(index, whatever) would return true.
     * If this returns, add(index, whatever) will work.
     * If it throws, it probably won't.
     */
    public synchronized void lock(int index) throws InterruptedException {
        boolean oldFlag = flag;
        if(minValue == -1) return;
        if(index - minValue < maxRange) return;
        Logger.normal(this, toString()+" lock("+index+") - minValue = "+minValue+", maxValue = "+maxValue+", maxRange="+maxRange);
        while(true) {
            wait();
            if(flag != oldFlag) throw new InterruptedException();
            if(index - minValue < maxRange) return;
        }
    }
    
    /**
     * Wait until add(index, whatever) would return true.
     * If this returns, add(index, whatever) will work.
     * If it throws, it probably won't.
     */
    public synchronized void lockNeverBlock(int index) throws WouldBlockException {
        if(minValue == -1) return;
        if(index - minValue < maxRange) return;
        Logger.normal(this, toString()+ " WOULD BLOCK: lockNeverBlock("+index+") - minValue = "+minValue+", maxValue = "+maxValue+", maxRange="+maxRange);
        throw new WouldBlockException();
    }
    
    public synchronized void remove(int index) {
        Logger.minor(this, "Removing "+index+" - min="+minValue+" max="+maxValue);
        if(contents.remove(new Integer(index)) != null) {
            if(index > minValue && index < maxValue) return;
            if(contents.size() == 0) {
                minValue = maxValue = -1;
                return;
            }
            if(index == maxValue) {
                for(int i=maxValue;i>=minValue;i--) {
                    Integer ii = new Integer(i);
                    if(contents.containsKey(ii)) {
                        maxValue = i;
                        return;
                    }
                }
                // Still here - WTF?
                throw new IllegalStateException("Still here! (a)");
            }
            if(index == minValue) {
                for(int i=minValue;i<=maxValue;i++) {
                    Integer ii = new Integer(i);
                    if(contents.containsKey(ii)) {
                        minValue = i;
                        return;
                    }
                }
                // Still here - WTF?
                throw new IllegalStateException("Still here! (b)");
            }
            throw new IllegalStateException("impossible");
        }
    }

    /**
     * @return The contents of each packet sent, then clear.
     */
    public synchronized byte[][] grabAllBytes() {
        int len = contents.size();
        byte[][] output = new byte[len][];
        Iterator i = contents.values().iterator();
        int count = 0;
        while(i.hasNext()) {
            output[count++] = (byte[])i.next();
        }
        clear();
        return output;
    }
    
    public synchronized LimitedRangeIntByteArrayMapElement[] grabAll() {
        int len = contents.size();
        LimitedRangeIntByteArrayMapElement[] output = new LimitedRangeIntByteArrayMapElement[len];
        Iterator i = contents.values().iterator();
        int count = 0;
        while(i.hasNext()) {
            output[count++] = (LimitedRangeIntByteArrayMapElement)i.next();
        }
        clear();
        return output;
    }
    
    /**
     * Empty the structure.
     */
    private void clear() {
        contents.clear();
        minValue = maxValue = -1;
    }
}
