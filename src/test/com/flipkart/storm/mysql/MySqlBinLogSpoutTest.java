///**
// * Copyright 2016 Flipkart Internet Pvt. Ltd.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.flipkart.storm.mysql;
//
//import backtype.storm.spout.SpoutOutputCollector;
//import backtype.storm.task.TopologyContext;
//import org.junit.Before;
//import org.junit.Test;
//import org.mockito.Mockito;
//import java.util.Map;
//
//public class MySqlBinLogSpoutTest {
//
//    private MySqlConfig             mySqlConfig;
//    private ZkBinLogStateConfig     zkBinLogStateConfig;
//    private MySqlSpoutConfig        mySqlSpoutConfig;
//    private Map<String, Object>     mockStormConfig;
//    private TopologyContext         mockTopologyContext;
//    private SpoutOutputCollector    mockSpoutOutputCollector;
//
//    @Before
//    public void init() {
//
//        mySqlConfig = new MySqlConfig.Builder("testDatabase").build();
//        zkBinLogStateConfig = new ZkBinLogStateConfig.Builder("my-spout").build();
//        mySqlSpoutConfig = new MySqlSpoutConfig(mySqlConfig, zkBinLogStateConfig);
//        mockStormConfig = Mockito.mock(Map.class);
//        mockTopologyContext = Mockito.mock(TopologyContext.class);
//        mockSpoutOutputCollector = Mockito.mock(SpoutOutputCollector.class);
//    }
//
//}