/*
 *  Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.kie.kogito.trusty.storage.infinispan;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.kogito.trusty.storage.api.model.FeatureImportance;

public class FeatureImportanceMarshaller extends AbstractModelMarshaller<FeatureImportance> {

    public FeatureImportanceMarshaller(ObjectMapper mapper) {
        super(mapper, FeatureImportance.class);
    }

    @Override
    public FeatureImportance readFrom(ProtoStreamReader reader) throws IOException {
        return new FeatureImportance(
                reader.readString(FeatureImportance.FEATURE_ID_FIELD),
                reader.readDouble(FeatureImportance.SCORE_FIELD)
        );
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, FeatureImportance input) throws IOException {
        writer.writeString(FeatureImportance.FEATURE_ID_FIELD, input.getFeatureId());
        writer.writeDouble(FeatureImportance.SCORE_FIELD, input.getScore());
    }

}
