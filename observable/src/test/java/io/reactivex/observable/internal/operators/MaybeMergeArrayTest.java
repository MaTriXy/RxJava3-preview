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

package io.reactivex.observable.internal.operators;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.Test;
import org.reactivestreams.*;

import io.reactivex.common.*;
import io.reactivex.common.exceptions.TestException;
import io.reactivex.observable.*;
import io.reactivex.observable.extensions.QueueDisposable;
import io.reactivex.observable.internal.operators.MaybeMergeArray.MergeMaybeObserver;
import io.reactivex.observable.subjects.PublishSubject;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.*;

public class MaybeMergeArrayTest {

    @SuppressWarnings("unchecked")
    @Test
    public void normal() {
        TestSubscriber<Integer> ts = SubscriberFusion.newTest(QueueDisposable.SYNC);

        Maybe.mergeArray(Maybe.just(1), Maybe.just(2))
        .subscribe(ts);
        ts
        .assertOf(SubscriberFusion.<Integer>assertFuseable())
        .assertOf(SubscriberFusion.<Integer>assertFusionMode(QueueDisposable.NONE))
        .assertResult(1, 2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void fusedPollMixed() {
        TestSubscriber<Integer> ts = SubscriberFusion.newTest(QueueDisposable.ANY);

        Maybe.mergeArray(Maybe.just(1), Maybe.<Integer>empty(), Maybe.just(2))
        .subscribe(ts);
        ts
        .assertOf(SubscriberFusion.<Integer>assertFuseable())
        .assertOf(SubscriberFusion.<Integer>assertFusionMode(QueueDisposable.ASYNC))
        .assertResult(1, 2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void fusedEmptyCheck() {
        Maybe.mergeArray(Maybe.just(1), Maybe.<Integer>empty(), Maybe.just(2))
        .subscribe(new FlowableSubscriber<Integer>() {
            QueueDisposable<Integer> qd;
            @Override
            public void onSubscribe(Subscription d) {
                qd = (QueueDisposable<Integer>)d;

                assertEquals(QueueDisposable.ASYNC, qd.requestFusion(QueueDisposable.ANY));
            }

            @Override
            public void onNext(Integer value) {
                assertFalse(qd.isEmpty());

                qd.clear();

                assertTrue(qd.isEmpty());

                qd.cancel();
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void cancel() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0L);

        Maybe.mergeArray(Maybe.just(1), Maybe.<Integer>empty(), Maybe.just(2))
        .subscribe(ts);

        ts.cancel();
        ts.request(10);

        ts.assertEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void firstErrors() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0L);

        Maybe.mergeArray(Maybe.<Integer>error(new TestException()), Maybe.<Integer>empty(), Maybe.just(2))
        .subscribe(ts);

        ts.assertFailure(TestException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void errorFused() {
        TestSubscriber<Integer> ts = SubscriberFusion.newTest(QueueDisposable.ANY);

        Maybe.mergeArray(Maybe.<Integer>error(new TestException()), Maybe.just(2))
        .subscribe(ts);
        ts
        .assertOf(SubscriberFusion.<Integer>assertFuseable())
        .assertOf(SubscriberFusion.<Integer>assertFusionMode(QueueDisposable.ASYNC))
        .assertFailure(TestException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void errorRace() {
        for (int i = 0; i < 500; i++) {
            List<Throwable> errors = TestCommonHelper.trackPluginErrors();

            try {
                final PublishSubject<Integer> ps1 = PublishSubject.create();
                final PublishSubject<Integer> ps2 = PublishSubject.create();

                TestSubscriber<Integer> ts = Maybe.mergeArray(ps1.singleElement(), ps2.singleElement())
                .test();

                final TestException ex = new TestException();

                Runnable r1 = new Runnable() {
                    @Override
                    public void run() {
                        ps1.onError(ex);
                    }
                };

                Runnable r2 = new Runnable() {
                    @Override
                    public void run() {
                        ps2.onError(ex);
                    }
                };

                TestCommonHelper.race(r1, r2, Schedulers.single());

                ts.assertFailure(Throwable.class);

                if (!errors.isEmpty()) {
                    TestCommonHelper.assertUndeliverable(errors, 0, TestException.class);
                }
            } finally {
                RxJavaPlugins.reset();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void mergeBadSource() {
        Maybe.mergeArray(new Maybe<Integer>() {
            @Override
            protected void subscribeActual(MaybeObserver<? super Integer> observer) {
                observer.onSubscribe(Disposables.empty());
                observer.onSuccess(1);
                observer.onSuccess(2);
                observer.onSuccess(3);
            }
        }, Maybe.never())
        .test()
        .assertResult(1, 2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void smallOffer2Throws() {
        Maybe.mergeArray(Maybe.never(), Maybe.never())
        .subscribe(new FlowableSubscriber<Object>() {

            @SuppressWarnings("rawtypes")
            @Override
            public void onSubscribe(Subscription s) {
                MergeMaybeObserver o = (MergeMaybeObserver)s;

                try {
                    o.queue.offer(1, 2);
                    fail("Should have thrown");
                } catch (UnsupportedOperationException ex) {
                    // expected
                }
            }

            @Override
            public void onNext(Object t) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void largeOffer2Throws() {
        Maybe<Integer>[] a = new Maybe[1024];
        Arrays.fill(a, Maybe.never());
        Maybe.mergeArray(a)
        .subscribe(new FlowableSubscriber<Object>() {

            @SuppressWarnings("rawtypes")
            @Override
            public void onSubscribe(Subscription s) {
                MergeMaybeObserver o = (MergeMaybeObserver)s;

                try {
                    o.queue.offer(1, 2);
                    fail("Should have thrown");
                } catch (UnsupportedOperationException ex) {
                    // expected
                }

                o.queue.drop();
            }

            @Override
            public void onNext(Object t) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
    }
}
