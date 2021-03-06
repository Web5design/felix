/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.dm.test.integration.api;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.test.components.Ensure;
import org.apache.felix.dm.test.integration.common.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class AspectDynamicsTest extends TestBase {
    @Test
    public void testDynamicallyAddAndRemoveAspect() {
        DependencyManager m = new DependencyManager(context);
        // helper class that ensures certain steps get executed in sequence
        Ensure e = new Ensure();
        // create a service provider and consumer
        Component provider = m.createComponent().setImplementation(new ServiceProvider(e)).setInterface(ServiceInterface.class.getName(), null);
        Component provider2 = m.createComponent().setImplementation(new ServiceProvider2(e)).setInterface(ServiceInterface2.class.getName(), null);
        Component consumer = m.createComponent().setImplementation(new ServiceConsumer(e)).add(m.createServiceDependency().setService(ServiceInterface.class).setRequired(true));
        Component aspect = m.createAspectService(ServiceInterface.class, null, 1, null).setImplementation(new ServiceAspect(e));
        
        m.add(consumer);
        m.add(provider);
        // the consumer should invoke the provider here, and when done, arrive at step 3
        // finally wait for step 6 before continuing
        e.waitForStep(3, 15000);
        
        m.add(aspect);
        // after adding the aspect, we wait for its init to be invoked, arriving at
        // step 4 after an instance bound dependency was added (on a service provided by
        // provider 2)
        e.waitForStep(4, 15000);
        
        m.add(provider2);
        
        // after adding provider 2, we should now see the aspect being started, so
        // we wait for step 5 to happen
        e.waitForStep(5, 15000);
        
        // now we continue with step 6, which will trigger the next part of the consumer's
        // run method to be executed
        e.step(6);
        
        // invoking step 7, 8 and 9 when invoking the aspect which in turn invokes the
        // dependency and the original service, so we wait for that to finish here, which
        // is after step 10 has been reached (the client will now wait for step 12)
        e.waitForStep(10, 15000);
        
        m.remove(aspect);
        // removing the aspect should trigger step 11 (in the stop method of the aspect)
        e.waitForStep(11, 15000);
        
        // step 12 triggers the client to continue
        e.step(12);
        
        // wait for step 13, the final invocation of the provided service (without aspect)
        e.waitForStep(13, 15000);
        
        // clean up
        m.remove(provider2);
        m.remove(provider);
        m.remove(consumer);
    }
    
    static interface ServiceInterface {
        public void invoke(Runnable run);
    }
    
    static interface ServiceInterface2 {
        public void invoke();
    }
    
    static class ServiceProvider2 implements ServiceInterface2 {
        private final Ensure m_ensure;

        public ServiceProvider2(Ensure ensure) {
            m_ensure = ensure;
        }

        public void invoke() {
            m_ensure.step(9);
        }
    }

    static class ServiceProvider implements ServiceInterface {
        private final Ensure m_ensure;
        public ServiceProvider(Ensure e) {
            m_ensure = e;
        }
        public void invoke(Runnable run) {
            run.run();
        }
    }
    
    static class ServiceAspect implements ServiceInterface {
        private final Ensure m_ensure;
        private volatile ServiceInterface m_originalService;
        private volatile ServiceInterface2 m_injectedService;
        private volatile Component m_service;
        private volatile DependencyManager m_manager;
        
        public ServiceAspect(Ensure e) {
            m_ensure = e;
        }
        public void init() {
            m_service.add(m_manager.createServiceDependency()
                .setService(ServiceInterface2.class)
                .setRequired(true)
                .setInstanceBound(true)
            );
            m_ensure.step(4);
        }
        public void start() {
            m_ensure.step(5);
        }
        public void invoke(Runnable run) {
            m_ensure.step(7);
            m_originalService.invoke(run);
            m_injectedService.invoke();
        }
        
        public void stop() {
            m_ensure.step(11);
        }
    }

    static class ServiceConsumer implements Runnable {
        private volatile ServiceInterface m_service;
        private final Ensure m_ensure;

        public ServiceConsumer(Ensure e) {
            m_ensure = e;
        }
        
        public void init() {
            Thread t = new Thread(this);
            t.start();
        }
        
        public void run() {
            m_ensure.step(1);
            m_service.invoke(Ensure.createRunnableStep(m_ensure, 2));
            m_ensure.step(3);
            m_ensure.waitForStep(6, 15000);
            m_service.invoke(Ensure.createRunnableStep(m_ensure, 8));
            m_ensure.step(10);
            m_ensure.waitForStep(12, 15000);
            m_service.invoke(Ensure.createRunnableStep(m_ensure, 13));
        }
    }
}
