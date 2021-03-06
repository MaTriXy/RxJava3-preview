/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.flowable.internal.subscriptions;

import java.util.List;

import org.junit.Test;

import io.reactivex.common.*;
import io.reactivex.flowable.TestHelper;
import io.reactivex.flowable.subscribers.TestSubscriber;

public class ScalarSubscriptionTest {

    @Test
    public void badRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0L);

        ScalarSubscription<Integer> sc = new ScalarSubscription<Integer>(ts, 1);

        List<Throwable> errors = TestCommonHelper.trackPluginErrors();
        try {
            sc.request(-99);

            TestCommonHelper.assertError(errors, 0, IllegalArgumentException.class, "n > 0 required but it was -99");
        } finally {
            RxJavaCommonPlugins.reset();
        }
    }

    @Test
    public void noOffer() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0L);

        ScalarSubscription<Integer> sc = new ScalarSubscription<Integer>(ts, 1);

        TestHelper.assertNoOffer(sc);
    }
}
