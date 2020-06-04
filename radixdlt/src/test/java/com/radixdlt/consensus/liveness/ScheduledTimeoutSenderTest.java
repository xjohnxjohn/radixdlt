/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.radixdlt.consensus.View;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScheduledTimeoutSenderTest {

	private ScheduledTimeoutSender scheduledTimeoutSender;
	private ScheduledExecutorService executorService;
	private ScheduledExecutorService executor;

	@Before
	public void setUp() {
		executor = Executors.newSingleThreadScheduledExecutor();
		ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> {
			// schedule submissions with a small timeout to ensure that control is returned before the
			// "scheduled" runnable is executed, otherwise required events may not be triggered in time
			executor.schedule((Runnable) invocation.getArguments()[0], 10, TimeUnit.MILLISECONDS);
			return null;
		}).when(executorService).schedule(any(Runnable.class), anyLong(), any());

		this.executorService = executorService;
		this.scheduledTimeoutSender = new ScheduledTimeoutSender(this.executorService);
	}

	@After
	public void tearDown() {
	    executor.shutdown();
	}

	@Test
	public void when_subscribed_to_local_timeouts_and_schedule_timeout__then_a_timeout_event_with_view_is_emitted() {
		TestObserver<View> testObserver = scheduledTimeoutSender.localTimeouts().test();
		View view = mock(View.class);
		long timeout = 10;
		scheduledTimeoutSender.scheduleTimeout(view, timeout);
		testObserver.awaitCount(1);
		testObserver.assertNotComplete();
		testObserver.assertValues(view);
		verify(executorService, times(1)).schedule(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void when_subscribed_to_local_timeouts_and_schedule_timeout_twice__then_two_timeout_events_are_emitted() {
		TestObserver<View> testObserver = scheduledTimeoutSender.localTimeouts().test();
		View view1 = mock(View.class);
		View view2 = mock(View.class);
		long timeout = 10;
		scheduledTimeoutSender.scheduleTimeout(view1, timeout);
		scheduledTimeoutSender.scheduleTimeout(view2, timeout);
		testObserver.awaitCount(2);
		testObserver.assertNotComplete();
		testObserver.assertValues(view1, view2);
		verify(executorService, times(2)).schedule(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void when_subscribed_to_timeout_and_timeout_occurs_for_view__then_should_complete() throws Exception {
		View view = View.of(0);
		TestObserver<Void> testObserver = scheduledTimeoutSender.timeout(view).test();
		scheduledTimeoutSender.scheduleTimeout(view, 10);
		testObserver.await();
		testObserver.assertComplete();
	}

	@Test
	public void when_subscribe_to_timeout_and_timeout_already_occurred__then_should_complete() throws Exception {
		View view = View.of(0);
		scheduledTimeoutSender.scheduleTimeout(view, 10);
		scheduledTimeoutSender.scheduleTimeout(view.next(), 10);
		TestObserver<Void> testObserver = scheduledTimeoutSender.timeout(view).test();
		testObserver.await();
		testObserver.assertComplete();
	}
}