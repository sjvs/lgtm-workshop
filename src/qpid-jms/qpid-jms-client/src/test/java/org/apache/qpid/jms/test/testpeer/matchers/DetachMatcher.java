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
 *
 */

package org.apache.qpid.jms.test.testpeer.matchers;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.jms.test.testpeer.AmqpPeerRunnable;
import org.apache.qpid.jms.test.testpeer.FrameType;
import org.apache.qpid.jms.test.testpeer.FrameWithNoPayloadMatchingHandler;
import org.hamcrest.Matcher;

/**
 * Generated by generate-matchers.xsl, which resides in this package.
 */
public class DetachMatcher extends FrameWithNoPayloadMatchingHandler
{
    /** Note that the ordinals of the Field enums match the order specified in the AMQP spec */
    public enum Field
    {
        HANDLE,
        CLOSED,
        ERROR,
    }

    public DetachMatcher()
    {
        super(FrameType.AMQP,
              ANY_CHANNEL,
              UnsignedLong.valueOf(0x0000000000000016L),
              Symbol.valueOf("amqp:detach:list"));
    }

    @Override
    public DetachMatcher onCompletion(AmqpPeerRunnable onCompletion)
    {
        super.onCompletion(onCompletion);
        return this;
    }

    public DetachMatcher withHandle(Matcher<?> m)
    {
        getMatchers().put(Field.HANDLE, m);
        return this;
    }

    public DetachMatcher withClosed(Matcher<?> m)
    {
        getMatchers().put(Field.CLOSED, m);
        return this;
    }

    public DetachMatcher withError(Matcher<?> m)
    {
        getMatchers().put(Field.ERROR, m);
        return this;
    }

    public Object getReceivedHandle()
    {
        return getReceivedFields().get(Field.HANDLE);
    }

    public Object getReceivedClosed()
    {
        return getReceivedFields().get(Field.CLOSED);
    }

    public Object getReceivedError()
    {
        return getReceivedFields().get(Field.ERROR);
    }

    @Override
    protected Enum<?> getField(int fieldIndex)
    {
        return Field.values()[fieldIndex];
    }
}

