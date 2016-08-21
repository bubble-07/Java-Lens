package com.bubble07.lenses;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A few skeletons. Implementations (aside from "chain") left as exercise to the dedicated reader.
 * @author bubble-07
 *
 */
public class Common {
	
	/**
	 * Chain a whole bunch o' functions together using "andThen"
	 * @param funcs
	 * @return
	 */
	@SafeVarargs
	public static <S> Function<S, S> chain(Function<S, S>... funcs) {
		Function<S, S> result = (s) -> s;
		for (Function<S, S> func : funcs) {
			result = result.andThen(func);
		}
		return result;
	}

	/**
	 * Represents a discriminated union A U B
	 * @author bubble-07
	 *
	 * @param <A>
	 * @param <B>
	 */
	public static class DiscrimUnion<A, B> {
		public Optional<A> first() {
			return null;
		}
		public Optional<B> second() {
			return null;
		}
		public <C> DiscrimUnion<C, B> leftMap(Function<A, C> in) {
			return null;
		}
		public <C> DiscrimUnion<A, C> rightMap(Function<B, C> in) {
			return null;
			
		}
	}
	/**
	 * Represents the cartesian product A x B
	 * @author bubble-07
	 *
	 * @param <A>
	 * @param <B>
	 */
	public static class Pair<A, B> {
		public static <A, B> Pair<A, B> of(A first, B second) {
			return null;
		}
		public static <A, B> Pair<B, B> onBoth(Pair<A,A> in, Function<A, B> action) {
			return null;
		}
		public static <A, B> void onBoth(Pair<A, A> in, Consumer<A> action) {
		}
		public A first() {
			return null;
		}
		public B second() {
			return null;
		}
		public <C> Pair<C, B> firstMap(Function<A, C> f) {
			return null;
		}
		public <C> Pair<A, C> secondMap(Function<B, C> f) {
			return null;
		}
	}
}
