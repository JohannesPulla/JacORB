package org.jacorb.poa;

/*
 *        JacORB - a free Java ORB
 *
 *   Copyright (C) 1997-2003  Gerald Brose.
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Library General Public
 *   License as published by the Free Software Foundation; either
 *   version 2 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this library; if not, write to the Free
 *   Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

import org.jacorb.poa.except.*;
import org.jacorb.poa.util.*;
import org.jacorb.poa.gui.*;

import org.jacorb.util.Debug;
import org.jacorb.util.Environment;
import org.jacorb.orb.dsi.ServerRequest;

import org.omg.PortableServer.*;
import org.omg.PortableServer.POAManagerPackage.State;

import org.apache.avalon.framework.logger.Logger;

import java.util.Enumeration;

/**
 * This class extends the POA with a monitoring gui. It implements all
 * poa related listener interfaces and will set up and update the gui.
 *
 * @author Reimo Tiedemann, FU Berlin
 * @version 1.06, 12/08/99, RT
 */

public class POAMonitorImpl
    extends POAAdapter
    implements POAMonitor, POAMonitorController
{
    private POA poaModel;
    private AOM aomModel;
    private RequestQueue queueModel;
    private RPPoolManager pmModel;

    private POAMonitorView view;

    private Logger logger;

    private String prefix;

    private int aomSize;
    private int queueSize;
    private int poolCount;
    private int poolSize;

    private Thread refreshThread;
    private boolean terminate;
    private boolean aomChanged;
    private boolean queueChanged;
    private boolean pmChanged;

    public void actionCloseView() {
        closeMonitor();
    }

    public void actionDeactivateObject(String oidStr) {

        if (poaModel != null) {
            try {
                poaModel.deactivate_object( oidStr.getBytes() );
            } catch (Throwable e) {
                printMessage("Exception occurred in deactivateObject() of POAMonitor: "+e);
            }
        }
    }

    public void actionRemoveRequestFromQueue(String ridStr) {

        if (queueModel != null && poaModel != null) {
            try {
                ServerRequest request = queueModel.getElementAndRemove(Integer.parseInt(ridStr));
                if (request == null) throw new ApplicationError("error: rid " + ridStr + " is not contained in queue");
                poaModel.getRequestController().rejectRequest(request, new org.omg.CORBA.OBJ_ADAPTER());
            } catch (Throwable e) {
                printMessage("Exception occurred in removeRequestFromQueue() of POAMonitor: "+e);
            }
        }
    }

    public StringPair[] actionRetrieveAOMContent() {

        if (aomModel != null) {
            try {
                return aomModel != null ? aomModel.deliverContent() : null;
            } catch (Throwable e) {
                printMessage("Exception occurred in retrieveAOMContent() of POAMonitor: "+e);
            }
        }
        return null;
    }

    public StringPair[] actionRetrieveQueueContent() 
    {
        if (queueModel != null) 
        {
            try 
            {
                return queueModel.deliverContent();
            }
            catch (Throwable e) 
            {
                printMessage("Exception during retrieveQueueContent() of POAMonitor: "+e);
            }
        }
        return null;
    }


    public synchronized void changeState(String state)
    {
        if (view != null)
        {
            try
            {
                view._setState(state);
            } 
            catch (Throwable exception)
            {
            	if (logger.isWarnEnabled()) 
                {
                    logger.warn("Exception during changeState() of POAMonitor" +
                                 exception.getMessage());
            	}
            }
        }
    }


    public synchronized void closeMonitor() 
    {
        if (view != null) 
        {
            try 
            {
                terminate = true;
                poaModel._removePOAEventListener(this);
                POAMonitor newMonitor = 
                    (POAMonitor)Class.forName("org.jacorb.poa.POAMonitorLightImpl").newInstance();
                newMonitor.init(poaModel, aomModel, queueModel, pmModel, prefix, logger);
                poaModel.setMonitor(newMonitor);
                POAMonitorView tmp = view;
                view = null;
                tmp._destroy();

            } 
            catch (Throwable exception) 
            {
            	if (logger.isWarnEnabled()) 
                {
	                logger.warn("Exception during closeMonitor() of POAMonitorImpl" +
                                    exception.getMessage());
            	}
            }
        }
    }


    public void init(POA poa, AOM aom, 
                     RequestQueue queue, RPPoolManager pm,
                     String _prefix, Logger _logger) 
    {
        poaModel = poa;
        aomModel = aom;
        queueModel = queue;
        pmModel = pm;
        prefix = prefix;
        logger = _logger;
    }


    private void initView() 
    {
        if (view != null) 
        {
            try 
            {
                String name = poaModel._getQualifiedName();
                view._setName(name.equals("") ? POAConstants.ROOT_POA_NAME :
                              POAConstants.ROOT_POA_NAME+POAConstants.OBJECT_KEY_SEPARATOR+name);

                view._setState(POAUtil.convert(poaModel.getState()));

                view._setPolicyThread(POAUtil.convert(poaModel.threadPolicy, THREAD_POLICY_ID.value));
                view._setPolicyLifespan(POAUtil.convert(poaModel.lifespanPolicy, LIFESPAN_POLICY_ID.value));
                view._setPolicyIdUniqueness(POAUtil.convert(poaModel.idUniquenessPolicy, ID_UNIQUENESS_POLICY_ID.value));
                view._setPolicyIdAssignment(POAUtil.convert(poaModel.idAssignmentPolicy, ID_ASSIGNMENT_POLICY_ID.value));
                view._setPolicyServantRetention(POAUtil.convert(
                                                                poaModel.servantRetentionPolicy, SERVANT_RETENTION_POLICY_ID.value));
                view._setPolicyRequestProcessing(POAUtil.convert(
                                                                 poaModel.requestProcessingPolicy, REQUEST_PROCESSING_POLICY_ID.value));
                view._setPolicyImplicitActivation(POAUtil.convert(
                                                                  poaModel.implicitActivationPolicy, IMPLICIT_ACTIVATION_POLICY_ID.value));

                view._initAOMBar(aomModel != null ? 10 : 0, true);

                view._initQueueBar(10, true);

                view._initActiveRequestsBar(poaModel.isSingleThreadModel() ? 1 : Environment.threadPoolMin(),
                                            poaModel.isSingleThreadModel() ? 1 : Environment.threadPoolMax());
                view._initThreadPoolBar(0);

            } 
            catch (Throwable exception) 
            {
            	if (logger.isWarnEnabled()) 
                {
	                logger.warn("Exception during initView() of POAMonitor" +
                                    exception.getMessage());
            	}
            }
        }
    }


    public void objectActivated(byte[] oid, Servant servant, int aom_size) 
    {
        aomSize = aom_size;
        aomChanged = true;
        refreshAOM();
    }


    public void objectDeactivated(byte[] oid, Servant servant, int aom_size) 
    {
        aomSize = aom_size;
        aomChanged = true;
        refreshAOM();
    }


    public synchronized void openMonitor() 
    {
        if (view == null) 
        {
            try 
            {
                aomSize = aomModel != null ? aomModel.size() : 0;
                queueSize = queueModel.size();
                poolCount = pmModel.getPoolCount();
                poolSize = pmModel.getPoolSize();

                view = new org.jacorb.poa.gui.poa.POAFrame(this);

                initView();
                refreshView();

                poaModel._addPOAEventListener(this);

                view._setVisible(true);

            } 
            catch (Throwable exception) 
            {
            	if (logger.isWarnEnabled()) 
                {
                    logger.warn("Exception occurred in openMonitor() of POAMonitor" +
                                exception.getMessage() );
            	}
            }
        }
    }


    private synchronized void printException(Throwable e)
    {
        if (view != null) 
        {
            try {
                view._printMessage("####################################################################");
                view._printMessage("\t"+e);
                view._printMessage("####################################################################");
            } 
            catch (Throwable exception) 
            {
                System.err.println("Exception occurred in _printException() of POAMonitor");
            }
        }
    }


//     public boolean test(int logLevel) 
//     {
//         return Environment.verbosityLevel() >= (Debug.POA | logLevel);
//     }


    private synchronized void printMessage(String str) 
    {
        if (view != null) 
        {
            try 
            {
                view._printMessage(Environment.time()+"> "+str);
            } 
            catch (Throwable exception) 
            {
                System.err.println("Exception occurred in _printMessage() of POAMonitor");
            }
        }
    }


    public void processorAddedToPool(RequestProcessor processor, 
                                     int pool_count, 
                                     int pool_size) 
    {
        poolCount = pool_count;
        poolSize = pool_size;
        pmChanged = true;
        refreshPM();
    }


    public void processorRemovedFromPool(RequestProcessor processor, 
                                         int pool_count, 
                                         int pool_size) 
    {
        poolCount = pool_count;
        poolSize = pool_size;
        pmChanged = true;
        refreshPM();
    }


    private /* synchronized */ void refreshAOM() 
    {
        if (view != null) 
        {
            try 
            {
                view._setValueAOMBar(aomSize);
            } 
            catch (Throwable exception) 
            {
            	if (logger.isWarnEnabled()) 
                {
                    logger.warn("Exception during refreshAOM() of POAMonitor" + 
                                     exception.getMessage());
            	}
            }
        }
    }


    private /* synchronized */ void refreshPM() 
    {
        if (view != null) 
        {
            try 
            {
                view._setValueActiveRequestsBar(poolSize-poolCount);
                view._setMaxThreadPoolBar(poolSize);
                view._setValueThreadPoolBar(poolCount);
            } 
            catch (Throwable exception) 
            {
            	if (logger.isWarnEnabled()) 
                {
	                logger.warn("Exception occurred in refreshPM() of POAMonitor" + 
                                    exception.getMessage());
            	}
            }
        }
    }


    private /* synchronized */ void refreshQueue() 
    {
        if (view != null) 
        {
            try 
            {
                view._setValueQueueBar(queueSize);
            } 
            catch (Throwable exception) 
            {
            	if (logger.isWarnEnabled()) 
                {
                    logger.warn("Exception occurred in refreshQueue() of POAMonitor: " +
                                exception.getMessage());
            	}
            }
        }
    }


    private void refreshView() 
    {
        refreshAOM();
        refreshQueue();
        refreshPM();
    }


    public void requestAddedToQueue(ServerRequest request, int queue_size) {
        queueSize = queue_size;
        queueChanged = true;
        refreshQueue();
    }


    public void requestRemovedFromQueue(ServerRequest request, int queue_size) {
        queueSize = queue_size;
        queueChanged = true;
        refreshQueue();
    }


}
