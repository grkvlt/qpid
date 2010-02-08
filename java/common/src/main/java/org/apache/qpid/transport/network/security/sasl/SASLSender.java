package org.apache.qpid.transport.network.security.sasl;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.util.Logger;

public class SASLSender extends SASLEncryptor implements Sender<ByteBuffer> {

    protected Sender<ByteBuffer> delegate;
    private byte[] appData;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private static final Logger log = Logger.get(SASLSender.class);
    
    public SASLSender(Sender<ByteBuffer> delegate)
    {
        this.delegate = delegate;
        log.debug("SASL Sender enabled");
    }
    
    @Override
    public void close() 
    {
        
        if (!closed.getAndSet(true))
        {
            delegate.close();
            if (isSecurityLayerEstablished())
            {
                try
                {
                    saslClient.dispose();
                } 
                catch (SaslException e)
                {
                    throw new SenderException("Error closing SASL Sender",e);
                }
            }
        }
    }

    @Override
    public void flush() 
    {
       delegate.flush();
    }

    @Override
    public void send(ByteBuffer buf) 
    {        
        if (closed.get())
        {
            throw new SenderException("SSL Sender is closed");
        }
        
        if (isSecurityLayerEstablished())
        {
            while (buf.hasRemaining())
            {
                int length = Math.min(buf.remaining(),sendBuffSize);
                log.debug("sendBuffSize %s", sendBuffSize);
                log.debug("buf.remaining() %s", buf.remaining());
                
                buf.get(appData, 0, length);
                try
                {
                    byte[] out = saslClient.wrap(appData, 0, length);
                    log.debug("out.length %s", out.length);
                    
                    delegate.send(ByteBuffer.wrap(out));
                } 
                catch (SaslException e)
                {
                    log.error("Exception while encrypting data.",e);
                    throw new SenderException("SASL Sender, Error occurred while encrypting data",e);
                }
            }            
        }
        else
        {
            delegate.send(buf);
        }        
    }

    @Override
    public void setIdleTimeout(int i) 
    {
        delegate.setIdleTimeout(i);
    }
    
    public void securityLayerEstablished()
    {
        appData = new byte[sendBuffSize];
        log.debug("SASL Security Layer Established");
    }

}