/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jupiter.rpc.load.balance;

import org.jupiter.transport.Directory;
import org.jupiter.transport.channel.CopyOnWriteGroupList;
import org.jupiter.transport.channel.JChannelGroup;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机负载均衡.
 *
 * <pre>
 * *****************************************************************************
 *
 *            random value
 * ─────────────────────────────────▶
 *                                  │
 *                                  ▼
 * ┌─────────────────┬─────────┬──────────────────────┬─────┬─────────────────┐
 * │element_0        │element_1│element_2             │...  │element_n        │
 * └─────────────────┴─────────┴──────────────────────┴─────┴─────────────────┘
 *
 * *****************************************************************************
 * </pre>
 *
 * jupiter
 * org.jupiter.rpc.load.balance
 *
 * @author jiachun.fjc
 */
public class RandomLoadBalancer implements LoadBalancer {

    private static final RandomLoadBalancer instance = new RandomLoadBalancer();

    public static RandomLoadBalancer instance() {
        return instance;
    }

    @Override
    public JChannelGroup select(CopyOnWriteGroupList groups, Directory directory) {
        JChannelGroup[] elements = groups.snapshot();
        int length = elements.length;

        if (length == 0) {
            return null;
        }

        if (length == 1) {
            return elements[0];
        }

        WeightArray weightsSnapshot = (WeightArray) groups.weightArray(elements, directory);

        if (weightsSnapshot == null) {
            weightsSnapshot = WeightUtil.allocWeightArray(length);

            if (WeightUtil.computeWeights(weightsSnapshot, length, elements, directory)) {
                groups.setWeightInfo(elements, directory, weightsSnapshot);
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (weightsSnapshot.isAllSameWeight()) {
            return elements[random.nextInt(length)];
        }

        // 这一段算法参考当前的类注释中的那张图
        //
        // 如果权重不相同且总权重大于0, 则按总权重数随机

        int sumWeight = weightsSnapshot.get(length - 1);
        int eVal = random.nextInt(sumWeight) % sumWeight;
        int eIndex = WeightUtil.binarySearchIndex(weightsSnapshot, length, eVal);

        return elements[eIndex];
    }
}
