package cz.xtf.tuple;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class Tuple {
	public static interface Pair<X, Y> extends Serializable {
		public X getFirst();

		public Y getSecond();
	}

	public static interface Triple<X, Y, Z> extends Serializable {
		public X getFirst();

		public Y getSecond();

		public Z getThird();
	}

	public static class PairImpl<X, Y> implements Pair<X, Y> {
		private static final long serialVersionUID = -114609472475893593L;
		private X first;
		private Y second;

		public PairImpl(X x, Y y) {
			this.first = x;
			this.second = y;
		}

		public X getFirst() {
			return first;
		}

		public Y getSecond() {
			return second;
		}

		@Override
		public String toString() {
			return "(" + first + ", " + second + ")";
		}
	}

	public static <X, Y> Pair<X, Y> pair(X x, Y y) {
		return new PairImpl<X, Y>(x, y);
	}

	public static <T, W> Function<Pair<T, W>, Pair<T, W>> pairBinarizeFirst(BiFunction<T, W, W> func) {
		return (Pair<T, W> p) -> {
			W w = func.apply(p.getFirst(), p.getSecond());
			return pair(p.getFirst(), w);
		};
	}

	public static <T, W> Function<Pair<T, W>, Pair<T, W>> pairIgnoreFirst(Function<W, W> func) {
		return (Pair<T, W> p) -> {
			W w = func.apply(p.getSecond());
			return pair(p.getFirst(), w);
		};
	}

	public static <T, W> Function<Pair<T, W>, Pair<T, W>> pairConsumeSecond(Consumer<W> func) {
		return (Pair<T, W> p) -> {
			func.accept(p.getSecond());
			return p;
		};
	}

	public static <X, Y, Z> Triple<X, Y, Z> triple(X x, Y y, Z z) {
		return new TripleImpl<X, Y, Z>(x, y, z);
	}

	public static class TripleImpl<X, Y, Z> implements Triple<X, Y, Z> {
		private static final long serialVersionUID = 4035047312787547257L;
		private X first;
		private Y second;
		private Z third;

		public TripleImpl(X x, Y y, Z z) {
			this.first = x;
			this.second = y;
			this.third = z;
		}

		public X getFirst() {
			return first;
		}

		public Y getSecond() {
			return second;
		}

		public Z getThird() {
			return third;
		}

		@Override
		public String toString() {
			return "(" + first + ", " + second + ", " + third + ")";
		}
	}
}
