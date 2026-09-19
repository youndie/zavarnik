# RQ7 steady state — 2026-09-19/20, bench-a/bench-b pair, JDK 25.0.4
# 90 s of load before the recording starts, 180 s recorded, 64 connections, real mode

## raw, as the runner prints it (includes the instrument's own bursts)
== dbitem  101 deoptimisations in 180 s = 33.67 per minute   (4788 rps)
       6  kotlin.coroutines.CoroutineContext$DefaultImpls.plus$lambda$0(CoroutineContext, CoroutineContext$Element)@78
       5  kotlinx.coroutines.CoroutineContextKt.newCoroutineContext(CoroutineScope, CoroutineContext)@1
       5  kotlinx.coroutines.CoroutineContextKt.foldCopies(CoroutineContext, CoroutineContext, boolean)@22
       5  kotlin.coroutines.CombinedContext.minusKey(CoroutineContext$Key)@68
       5  io.netty.util.DefaultAttributeMap.searchAttributeByKey(DefaultAttributeMap$DefaultAttribute[], AttributeKey)@9
   reasons: "class_check"=44, "unstable_if"=39, "speculate_class_check"=9, "bimorphic_or_optimized_type_check"=7, "unstable_fused_if"=1
== dblist  94 deoptimisations in 180 s = 31.33 per minute   (2974 rps)
       6  java.nio.ByteBuffer.putArray(int, byte[], int, int)@97
       5  kotlinx.coroutines.CoroutineContextKt.newCoroutineContext(CoroutineScope, CoroutineContext)@1
       5  kotlin.coroutines.CoroutineContext$DefaultImpls.plus$lambda$0(CoroutineContext, CoroutineContext$Element)@78
       4  io.netty.channel.DefaultMaxMessagesRecvByteBufAllocator$MaxMessageHandle.continueReading(UncheckedBooleanSupplier)@4
       4  io.netty.channel.DefaultChannelPipeline$HeadContext.readIfIsAutoRead()@7
   reasons: "unstable_if"=37, "class_check"=36, "bimorphic_or_optimized_type_check"=7, "predicate"=6, "speculate_class_check"=4
== dbpost  75 deoptimisations in 180 s = 25.00 per minute   (1768 rps)
       5  io.netty.channel.DefaultMaxMessagesRecvByteBufAllocator$MaxMessageHandle.continueReading(UncheckedBooleanSupplier)@4
       4  java.util.Arrays.fill(Object[], int, int, Object)@20
       4  java.util.Arrays.fill(Object[], Object)@13
       4  io.netty.channel.DefaultChannelPipeline$HeadContext.readIfIsAutoRead()@7
       4  io.netty.util.concurrent.DefaultPromise.notifyListeners()@1
   reasons: "unstable_if"=38, "class_check"=17, "array_check"=8, "speculate_class_check"=5, "predicate"=2
DONE

## deoptimisations over the window, 10-second buckets
dbitem  59 2 5 1 0 1 0 1 0 0 0 0 2 3 0 0 0 0 16
dblist  62 3 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0 0 17
dbpost  37 0 0 0 0 0 0 1 1 0 2 3 1 2 2 0 0 2  5
# JFR.start and JFR.stop each force recompilation; the steady state is the middle.

## steady state, first and last 10 s excluded
== dbitem  steady window 160 s: 15 events = 5.62/min, 17.4 per million requests
   actions: maybe_recompile=9, reinterpret=6
   reasons: speculate_class_check=9, unstable_if=6
       5 x  java.lang.Thread.isVirtual()@1   intervals: 109s, 3s, 6s, 0s
       4 x  kotlinx.coroutines.AbstractCoroutine.onCompletionInternal(Object)@1   intervals: 0s, 0s, 0s
       1 x  java.io.RandomAccessFile.readBytes(byte[], int, int)@3
       1 x  kotlinx.coroutines.DispatchedCoroutine.trySuspend()@57

== dblist  steady window 160 s: 6 events = 2.25/min, 11.2 per million requests
   actions: reinterpret=5, maybe_recompile=1
   reasons: unstable_if=5, speculate_class_check=1
       1 x  java.io.RandomAccessFile.readBytes(byte[], int, int)@3
       1 x  kotlinx.coroutines.internal.LimitedDispatcher.dispatch(CoroutineContext, Runnable)@29
       1 x  kotlinx.coroutines.JobSupport.cancelMakeCompleting(Object)@19
       1 x  java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.canReacquire(AbstractQueuedSynchronizer$ConditionNode)@18

== dbpost  steady window 160 s: 12 events = 4.50/min, 37.7 per million requests
   actions: reinterpret=10, maybe_recompile=2
   reasons: unstable_if=10, predicate=1, speculate_class_check=1
       4 x  kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.tryPark()@40   intervals: 31s, 26s, 16s
       1 x  sun.security.provider.NativePRNG$RandomIO.ensureBufferValid()@77
       1 x  jdk.internal.event.SocketReadEvent.commit(long, long, String, String, int, long, long, boolean)@76
       1 x  sun.nio.ch.NioSocketImpl.write(byte[], int, int)@31

## exceptions - throttled sample vs the untrottled counter
== dbitem  52406 throws over 861840 requests = 0.061 per request
     50844  kotlinx.coroutines.JobCancellationException (classLoader = app)
      1562  io.netty.util.ResourceLeakDetector$TraceRecord (classLoader = app)
   top frame    50844  java.lang.Throwable.<init>(String) line: 286
   top frame     1562  java.lang.Throwable.<init>() line: 266

== dblist  52191 throws over 535320 requests = 0.097 per request
     49817  kotlinx.coroutines.JobCancellationException (classLoader = app)
      2353  io.netty.util.ResourceLeakDetector$TraceRecord (classLoader = app)
        14  io.netty.channel.StacklessClosedChannelException (classLoader = app)
         6  java.util.concurrent.CancellationException (classLoader = bootstrap)
   top frame    49823  java.lang.Throwable.<init>(String) line: 286
   top frame     2367  java.lang.Throwable.<init>() line: 266
   top frame        1  java.lang.Throwable.<init>(String, Throwable) line: 313

== dbpost  51607 throws over 318240 requests = 0.162 per request
     49997  kotlinx.coroutines.JobCancellationException (classLoader = app)
      1573  io.netty.util.ResourceLeakDetector$TraceRecord (classLoader = app)
        28  io.netty.channel.StacklessClosedChannelException (classLoader = app)
         9  java.util.concurrent.CancellationException (classLoader = bootstrap)
   top frame    50006  java.lang.Throwable.<init>(String) line: 286
   top frame     1601  java.lang.Throwable.<init>() line: 266


== dbitem  cumulative throwables 2 -> 888764  = 888762 over 180 s = 4938/s, 1.031 per request
== dblist  cumulative throwables 88 -> 559878  = 559790 over 180 s = 3110/s, 1.046 per request
== dbpost  cumulative throwables 157 -> 328468  = 328311 over 180 s = 1824/s, 1.032 per request

## exception construction as a share of request CPU (committed pair profiles)
dbitem   samples= 84270  exception frames: leaf 0.154%  anywhere-on-stack(overcounts) 0.73%
      any:CancellationException                    93  0.110%
      any:Exception.<init>                         58  0.069%
      any:JobCancellationException                 93  0.110%
      any:Throwable.<init>                        185  0.220%
      any:fillInStackTrace                        182  0.216%
      leaf:CancellationException                   52  0.062%
      leaf:Exception.<init>                        19  0.023%
      leaf:JobCancellationException                52  0.062%
      leaf:Throwable.<init>                         3  0.004%
      leaf:fillInStackTrace                         4  0.005%
dblist   samples= 91563  exception frames: leaf 0.056%  anywhere-on-stack(overcounts) 0.61%
      any:CancellationException                    45  0.049%
      any:Exception.<init>                         26  0.028%
      any:JobCancellationException                 45  0.049%
      any:Throwable.<init>                        223  0.244%
      any:fillInStackTrace                        222  0.242%
      leaf:CancellationException                   21  0.023%
      leaf:Exception.<init>                         2  0.002%
      leaf:JobCancellationException                21  0.023%
      leaf:Throwable.<init>                         1  0.001%
      leaf:fillInStackTrace                         6  0.007%
dbpost   samples= 77475  exception frames: leaf 0.094%  anywhere-on-stack(overcounts) 0.58%
      any:CancellationException                    57  0.074%
      any:Exception.<init>                         34  0.044%
      any:JobCancellationException                 57  0.074%
      any:Throwable.<init>                        152  0.196%
      any:fillInStackTrace                        152  0.196%
      leaf:CancellationException                   30  0.039%
      leaf:Exception.<init>                         8  0.010%
      leaf:JobCancellationException                30  0.039%
      leaf:Throwable.<init>                         1  0.001%
      leaf:fillInStackTrace                         4  0.005%
