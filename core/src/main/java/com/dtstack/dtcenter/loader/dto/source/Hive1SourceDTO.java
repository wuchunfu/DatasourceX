/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.dtcenter.loader.dto.source;

import com.dtstack.dtcenter.loader.source.DataSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 17:54 2020/5/22
 * @Description：Hive1 数据源信息
 */
@Data
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Hive1SourceDTO extends RdbmsSourceDTO {
    /**
     * Hadoop defaultFS
     */
    @Builder.Default
    private String defaultFS = "";

    /**
     * Hadoop/ Hbase 配置信息
     */
    private String config;

    /**
     * hive ssl
     */
    private HiveSslConfig hiveSslConfig;

    @Override
    public Integer getSourceType() {
        return DataSourceType.HIVE1X.getVal();
    }
}
