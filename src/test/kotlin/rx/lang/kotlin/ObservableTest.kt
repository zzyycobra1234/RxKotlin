package rx.lang.kotlin

import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class ObservableTest {

    @Test fun testCreation() {
        val observable = observable<Int> { s ->
            s.apply {
                onNext(1)
                onNext(777)
                onComplete()
            }
        }

        assertEquals(listOf(1, 777), observable.toList().blockingGet())

        val o0: Observable<Int> = Observable.empty()
        val o1: Observable<Int> = listOf(1, 2, 3).toObservable()
        val o2: Observable<List<Int>> = Observable.just(listOf(1, 2, 3))

        val o3: Observable<Int> = Observable.defer { observable<Int> { s -> s.onNext(1) } }
        val o4: Observable<Int> = Array(3) { 0 }.toObservable()
        val o5: Observable<Int> = IntArray(3).toObservable()

        assertNotNull(o0)
        assertNotNull(o1)
        assertNotNull(o2)
        assertNotNull(o3)
        assertNotNull(o4)
        assertNotNull(o5)
    }

    @Test fun testExampleFromReadme() {
        val observable = observable<String> { s ->
            s.apply {
                onNext("H")
                onNext("e")
                onNext("l")
                onNext("")
                onNext("l")
                onNext("o")
                onComplete()
            }
        }
        val result = observable
                .filter(String::isNotEmpty)
                .fold(StringBuilder(), StringBuilder::append)
                .map { it.toString() }
                .blockingGet()

        assertEquals("Hello", result)
    }

    @Test fun iteratorObservable() {
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).iterator().toObservable().toList().blockingGet())
    }

    @Test fun intProgressionStep1Empty() {
        assertEquals(listOf(1), (1..1).toObservable().toList().blockingGet())
    }

    @Test fun intProgressionStep1() {
        assertEquals((1..10).toList(), (1..10).toObservable().toList().blockingGet())
    }

    @Test fun intProgressionDownTo() {
        assertEquals((1 downTo 10).toList(), (1 downTo 10).toObservable().toList().blockingGet())
    }

    @Ignore("Too slow")
    @Test fun intProgressionOverflow() {
        val result = (-10..Integer.MAX_VALUE).toObservable()
                .skip(Integer.MAX_VALUE.toLong())
                .map { Integer.MAX_VALUE - it }
                .toList()
                .blockingGet()
        assertEquals((0..10).toList().reversed(), result)
    }

    @Test fun testWithIndex() {
        listOf("a", "b", "c").toObservable()
                .withIndex()
                .toList()
                .test()
                .assertValues(listOf(IndexedValue(0, "a"), IndexedValue(1, "b"), IndexedValue(2, "c")))
    }

    @Test fun `withIndex() shouldn't share index between multiple subscribers`() {
        val o = listOf("a", "b", "c").toObservable().withIndex()

        val subscriber1 = TestObserver.create<IndexedValue<String>>()
        val subscriber2 = TestObserver.create<IndexedValue<String>>()

        o.subscribe(subscriber1)
        o.subscribe(subscriber2)

        subscriber1.awaitTerminalEvent()
        subscriber1.assertValues(IndexedValue(0, "a"), IndexedValue(1, "b"), IndexedValue(2, "c"))

        subscriber2.awaitTerminalEvent()
        subscriber2.assertValues(IndexedValue(0, "a"), IndexedValue(1, "b"), IndexedValue(2, "c"))
    }

    @Test fun testFold() {
        val result = listOf(1, 2, 3).toObservable().fold(0) { acc, e -> acc + e }.blockingGet()
        assertEquals(6, result)
    }

    @Test fun `kotlin sequence should produce expected items and observable be able to handle em`() {
        generateSequence(0) { it + 1 }.toObservable()
                .take(3)
                .toList()
                .test()
                .assertValues(listOf(0, 1, 2))
    }

    @Test fun `infinite iterable should not hang or produce too many elements`() {
        val generated = AtomicInteger()
        generateSequence { generated.incrementAndGet() }.toObservable().
                take(100).
                toList().
                subscribe()

        assertEquals(100, generated.get())
    }

    @Test fun testFlatMapSequence() {
        assertEquals(
                listOf(1, 2, 3, 2, 3, 4, 3, 4, 5),
                listOf(1, 2, 3).toObservable().flatMapSequence { listOf(it, it + 1, it + 2).asSequence() }.toList().blockingGet()
        )
    }

    @Test fun testCombineLatest() {
        val list = listOf(1, 2, 3, 2, 3, 4, 3, 4, 5)
        assertEquals(list, list.map { Observable.just(it) }.combineLatest { it }.blockingFirst())
    }

    @Test fun testZip() {
        val list = listOf(1, 2, 3, 2, 3, 4, 3, 4, 5)
        assertEquals(list, list.map { Observable.just(it) }.zip { it }.blockingFirst())
    }

    @Test fun testCast() {
        val source = Observable.just<Any>(1, 2)
        val observable = source.cast<Int>()
        observable.test()
                .await()
                .assertValues(1, 2)
                .assertNoErrors()
                .assertComplete()
    }

    @Test fun testCastWithWrongType() {
        val source = Observable.just<Any>(1, 2)
        val observable = source.cast<String>()
        observable.test()
                .assertError(ClassCastException::class.java)
    }

    @Test fun testOfType() {
        val source = Observable.just<Number>(BigDecimal.valueOf(15, 1), 2, BigDecimal.valueOf(42), 15)

        source.ofType<Int>()
                .test()
                .await()
                .assertValues(2, 15)
                .assertNoErrors()
                .assertComplete()

        source.ofType<BigDecimal>()
                .test()
                .await()
                .assertValues(BigDecimal.valueOf(15, 1), BigDecimal.valueOf(42))
                .assertNoErrors()
                .assertComplete()

        source.ofType<Double>()
                .test()
                .await()
                .assertNoValues()
                .assertNoErrors()
                .assertComplete()

        source.ofType<Comparable<*>>()
                .test()
                .await()
                .assertValues(BigDecimal.valueOf(15, 1), 2, BigDecimal.valueOf(42), 15)
                .assertNoErrors()
                .assertComplete()

    }

}