/*
 *  Copyright (c) 2017 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.extension.siddhi.io.jms.sink;

import org.apache.log4j.Logger;
import org.wso2.carbon.transport.jms.sender.JMSClientConnector;
import org.wso2.carbon.transport.jms.utils.JMSConstants;
import org.wso2.extension.siddhi.io.jms.util.JMSOptionsMapper;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.stream.output.sink.Sink;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.core.util.transport.DynamicOptions;
import org.wso2.siddhi.core.util.transport.Option;
import org.wso2.siddhi.core.util.transport.OptionHolder;
import org.wso2.siddhi.query.api.definition.StreamDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * JMS output transport class.
 * Dynamic options: destination
 */
@Extension(
        name = "jms",
        namespace = "sink",
        description = "JMS Output Transport",
        examples = @Example(description = "TBD", syntax = "TBD")
)
public class JMSSink extends Sink {
    private static final Logger log = Logger.getLogger(JMSSink.class);
    private OptionHolder optionHolder;
    private JMSClientConnector clientConnector;
    private Option destination;
    private Map<String, String> jmsStaticProperties;
    private ExecutorService executorService;

    @Override
    protected void init(StreamDefinition outputStreamDefinition, OptionHolder optionHolder,
                        ConfigReader sinkConfigReader, SiddhiAppContext executionPlanContext) {
        this.optionHolder = optionHolder;
        this.destination = optionHolder.getOrCreateOption(JMSConstants.DESTINATION_PARAM_NAME, null);
        this.jmsStaticProperties = initJMSProperties();
        this.executorService = executionPlanContext.getExecutorService();
    }

    @Override
    public void connect() throws ConnectionUnavailableException {
        this.clientConnector = new JMSClientConnector();
    }

    @Override
    public Map<String, Object> currentState() {
        return null;
    }

    @Override
    public void restoreState(Map<String, Object> state) {

    }

    @Override
    public void publish(Object payload, DynamicOptions transportOptions) {
        String topicQueueName = destination.getValue(transportOptions);
        try {
            executorService.submit(new JMSPublisher(topicQueueName, jmsStaticProperties, clientConnector, payload));
        } catch (RejectedExecutionException e) {
            //No need to retry as we are using an unbounded queue. Only place this can happen is when the executor
            // service is shutting down. In such cases we can't anyway handle the exception. Hence logging.
            log.error("Error occured when submitting following payload to be published via JMS. Payload : " + payload
                    .toString(), e);
        }
    }

    @Override
    public String[] getSupportedDynamicOptions() {
        return new String[]{JMSConstants.DESTINATION_PARAM_NAME};
    }

    @Override
    public void disconnect() {

    }

    @Override
    public void destroy() {

    }

    /**
     * Initializing JMS properties.
     * The properties in the required options list are mandatory.
     * Other JMS options can be passed in as key value pairs, key being in the JMS spec or the broker spec.
     *
     * @return all the options map.
     */
    private Map<String, String> initJMSProperties() {
        List<String> requiredOptions = JMSOptionsMapper.getRequiredOptions();
        Map<String, String> customPropertyMapping = JMSOptionsMapper.getCarbonPropertyMapping();
        // getting the required values
        Map<String, String> transportProperties = new HashMap<>();
        requiredOptions.forEach(requiredOption ->
                transportProperties.put(customPropertyMapping.get(requiredOption),
                        optionHolder.validateAndGetStaticValue(requiredOption)));
        // getting optional values
        optionHolder.getStaticOptionsKeys().stream()
                .filter(option -> !requiredOptions.contains(option) && !option.equals("type")).forEach(option ->
                transportProperties.put(option, optionHolder.validateAndGetStaticValue(option)));
        return transportProperties;
    }
}
