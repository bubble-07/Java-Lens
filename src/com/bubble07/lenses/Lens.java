package com.bubble07.lenses;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Java Lens Library (bubble-07)
Copyright (c) 2016 - Alexander Grabanski 

Redistribution and use, with or without modification, are permitted
provided that the following condition is met:

 *  Redistributions of this code must retain the above copyright notice,
this condition and the following disclaimer.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * The "1-clause" BSD licensing of this file and this file only is intentional, as I don't like useless legalese
 * on trivial things like encapsulation of Java fields, which I don't pretend to have invented.
 * Hell, I didn't even invent lenses. This is just my way of reinventing that particular wheel in Java.
 * 
 * To actually use this, you'll need to drop in your favorite implementation of Pair, because that's a wheel that
 * just about everybody reinvents at least once ;)
 */

/**
 * Represents a Lens, a la Haskell. In short, a Lens<S, T, A, B> is something that tells you
 * how to transform an S to a T given a function from an A to a B. This is useful (when combined with the "focus" operation)
 * for updating deeply-nested structures, or just multiple fields of an object in the same way (SelfLens/FieldLens with "bothOf"). 
 * <br><br>
 * How these lenses relate to Haskell's lenses:
 * These lenses are Haskell's an emulation of Haskell's Lens instantiated with the identity functor. This is done because Java's type system
 * makes it impossible (to the best of my knowledge) to faithfully represent the Functor typeclass in Haskell in a convenient manner.
 * What this means for the everyday user is that lens operations on functor-returning transformations need explicit lifts
 * using constructs like "optional()" or "list()" as below. In addition, some of these functorial constructs don't play nice with mutation,
 * so make sure to read their documentation carefully.
 * <br><br>
 * Some liberties have been taken to express some of the "kitchen sink" functionality of Lens in Haskell
 * without requiring emulation of the Functor typeclass. As a result, some of the things that are called lenses here would not be considered
 * lenses (but maybe prisms or antiprisms) in Haskell. The official position of this library is that anything that matches the signature
 * (A -> B) -> (S -> T) and has something to do with focusing on data members of a structure can be called a Lens.
 * <br><br>
 * In addition, due to the imperative nature of Java, support is also provided for lenses which mutate structures.
 * See {@linkplain FieldLens}
 * 
 * @author bubble-07
 *
 * @param <S>
 * @param <T>
 * @param <A>
 * @param <B>
 */
public interface Lens<S, T, A, B> extends Function<Function<A, B>, Function<S, T>> {
	
	/**
	 * In the lens type Lens<S, T, A, B>, which tells you how to transform a S to a T given a function from A to B,
	 * given something that tells you how to transform an A to a B from a function from C to D,
	 * this returns a lens of type Lens<S, T, C, D> which tells you how to transform a S to a T given a function from C to D.
	 * <br><br>
	 * Captures the notion of "focusing" on a substructure of a given instance/collection/etc in a functional style.
	 * By far the most common Lens operation -- can almost be thought of as Java's "." operator for extracting member variables.
	 * @param inner
	 * @return
	 */
	public default <C, D> Lens<S, T, C, D> focus(Lens<A, B, C, D> inner) {
		return (cdTransformer) -> this.apply(inner.apply(cdTransformer));
	}
	
	/**
	 * A reversed version of {@linkplain Lens#focus} -- that is, x.focus(y) is equivalent to y.within(x)
	 * @param outer
	 * @return
	 */
	public default <SContainer, TContainer> Lens<SContainer, TContainer, A, B> within(Lens<SContainer, TContainer, S, T> outer) {
		return outer.focus(this);
	}
	
	/**
	 * Lifts a lens which tells you how to take a function from A to B and turn it into a transformation from S to T
	 * into a lens which tells you how to take a function from A to an Optional B and turn it into a transformation from S to an Optional T 
	 * by mapping inhabited Optional B values to an inhabited Optional T, and empty Optional B values to uninhabited Optional T values.
	 * <br><br>
	 * Algebraically: <br>
	 * L.optional().apply(a -> Optional.of(f(a))).apply(s) = Optional.of(L.apply(a -> f(a)).apply(s)) <br> and <br>
	 * L.optional().apply(a -> Optional.empty()).apply(s) = Optional.empty()
	 * <br><br>
	 * Useful for introducing the possibility of failure into a lensing operation. 
	 * <br><br>
	 * In the case that the lifted lens is a <b>mutating</b> ({@linkplain Lens#FieldLens}) lens,
	 * this operation will cause mutation of member variables to occur only if the Optional B value turns out to be inhabited.
	 * The underlying T (=S, in the case of field lenses -- the instance whose fields are to be mutated) will remain unchanged.
	 * @return
	 */
	public default Lens<S, Optional<T>, A, Optional<B>> optional() {
		//The implementation of this is really evil, because it exploits the inversion of control
		//given by exceptions. Then again, this is probably the easiest way to do it.
		return (f) -> ((s) -> {
			try {
				return Optional.of(this.apply(f.andThen(Optional::get)).apply(s));
			}
			catch (NoSuchElementException e) {
				return Optional.empty();
			}
		});
	}
	
	/**
	 * Lifts a lens which tells you how to take a function from A to B and turn it into a transformation from S to T
	 * into a lens which tells you how to take a function from A to a list of Bs and turn it into a transformation from S to a list of Ts
	 * by interpreting the list of Bs as a list of possible transformations from the given A value to Bs and returning the result
	 * of applying the lens to each given transformation.
	 * <br><br>
	 * Satisfies the equation (with "fs" a finite stream of functions): <br>
	 * L.list().apply(a -> fs.map(f -> f.apply(a)).collect(Collectors.toList())).apply(s) = <br>
	 * fs.map(f -> L.apply(a -> f(a)).apply(s)).collect(Collectors.toList())
	 * 
	 * <br><br>
	 * Useful for introducing nondeterminism into a lensing operation.
	 * <br><br>
	 * <b>Warning:</b> This will <b>not</b> play nicely with mutating lenses, since a lens knows nothing about
	 * how to properly copy an instance of an arbitrary object. Use the {@linkplain SelfLens#list(Function)} method instead,
	 * which requires explicit specification of a "copy" method!
	 * 
	 * @return
	 */
	public default Lens<S, List<T>, A, List<B>> list() {
		//The implementation of this, too, is evil. The real problem is that we don't know ahead-of-time
		//how many list elements we'll get from applying the lens, and we don't know that all lenses in the chain
		//won't do anything evil like mutate member variables!
		return (f) -> ((s) -> {
			List<T> result = new ArrayList<>();
			try {
				for (int i = 0; true; i++) {
					final int iFinal = i;
					result.add(this.apply(f.andThen((list) -> list.get(iFinal))).apply(s));
				}
			}
			catch (IndexOutOfBoundsException e) { }
			return result;
		});
	}
	
	/**
	 * Static version of the {@link Lens#focus(Lens)} method. Exists because Java generic inference (for some godforsaken reason)
	 * sucks when dealing with a chain of instance method calls. If the type of the first lens in a lensing operation isn't explicitly specified, 
	 * you may need to break this out.
	 * @param outer
	 * @param inner
	 * @return
	 */
	public static <A, B, C, D, S, T> Lens<S, T, C, D> focus(Lens<S, T, A, B> outer, Lens<A, B, C, D> inner) {
		return outer.focus(inner);
	}
	
	/**
	 * Static version of lens application. Exists because Java generic inference (for some godforsaken reason)
	 * sucks when dealing with a chain of instance method calls. If the type of the lens in a lensing application isn't explicitly specified,
	 * you may need to break this out.
	 * @param lens
	 * @param transform
	 * @return
	 */
	public static <S, T, A, B> Function<S, T> apply(Lens<S, T, A, B> lens, Function<A, B> transform) {
		return lens.apply(transform);
	}
	
	/**
	 * @return The identity lens, which takes a f:S->S and returns it unchanged.
	 */
	public static <S> SelfLens<S, S> id() {
		return f -> f;
	}
	
	/**
	 * @return A lens which takes functions from A to B and maps them over lists. 
	 */
	public static <A, B> Lens<List<A>, List<B>, A, B> listElements() {
		return (f) -> ((l) -> l.stream().map(f).collect(Collectors.toList()));
	}
	
	/**
	 * @return A lens which focuses on transformations: A -> (A -> A) to reduce a List A to an Optional A.
	 */
	public static <A, B> Lens<List<A>, Optional<A>, A, Function<A, A>> listReduce() {
		return (f) -> ((l) -> l.stream().reduce((a, b) -> f.apply(a).apply(b)));
	}
	
	/**
	 * @return A lens which focuses on the return value of a function (to perform additional operations)<br><br>
	 * To category theory geeks: This operates kinda like the covariant Hom functor if you squint hard enough.
	 */
	public static <A, B, C> Lens<Function<A, B>, Function<A, C>, B, C> functionReturn() {
		return (morphism) -> ((f) -> f.andThen(morphism));
	}
	
	/**
	 * @return A lens which focuses on the argument of a function (by precomposition)<br><br>
	 * To category theory geeks: This operates kinda like the contravariant Hom functor if you squint hard enough.
	 */
	public static <A, B, C> Lens<Function<A, B>, Function<C, B>, C, A> functionArg() {
		return (morphism) -> ((f) -> f.compose(morphism));
	}
	
	/**
	 * @return A lens which takes functions and maps them over streams.
	 */
	public static <A, B> Lens<Stream<A>, Stream<B>, A, B> streamElements() {
		return (f) -> ((s) -> s.map(f));
	}
	
	/**
	 * @return A lens which takes functions from A to Stream B and flatMaps them over streams of As. 
	 */
	public static <A, B> Lens<Stream<A>, Stream<B>, A, Stream<B>> flatMapStream() {
		return (f) -> ((s) -> s.flatMap(f));
	}
	
	/**
	 * @return A lens which takes functions and maps them over sets.
	 */
	public static <A, B> Lens<Set<A>, Set<B>, A, B> setElements() {
		return (f) -> ((s) -> s.stream().map(f).collect(Collectors.toSet()));
	}
	
	/**
	 * @return A lens which takes functions and maps them over values wrapped in the Optional functor.
	 */
	public static <A, B> Lens<Optional<A>, Optional<B>, A, B> optionalElement() {
		return (f) -> ((opt) -> opt.map(f));
	}
	
	/**
	 * @return A lens which takes functions from A to Optional B and flatMaps them over Optional As.
	 */
	public static <A, B> Lens<Optional<A>, Optional<B>, A, Optional<B>> flatMapOptional() {
		return (f) -> ((opt) -> opt.flatMap(f));
	}
	
	/**
	 * @return A lens which focuses (simultaneously) on both elements of a pair of objects of the same type
	 */
	public static <A, B> Lens<Common.Pair<A, A>, Common.Pair<B, B>, A, B> pairBoth() {
		return (f) -> ((pair) -> Common.Pair.onBoth(pair, f));
	}
	
	/**
	 * @return A lens which focuses on the first element of a pair.
	 */
	public static <A, B, C> Lens<Common.Pair<A, B>, Common.Pair<C, B>, A, C> pairFirst() {
		return (f) -> ((pair) -> pair.firstMap(f));
	}
	/**
	 * @return A lens which focuses on the second element of a pair
	 */
	public static <A, B, C> Lens<Common.Pair<A, B>, Common.Pair<A, C>, B, C> pairSecond() {
		return (f) -> ((pair) -> pair.secondMap(f));
	}
	
	/**
	 * Given a lens focusing on an A within an S, and a lens focusing on a B within an S,
	 * yields a lens which focuses on the product A x B within an S, where the effects of
	 * applying the resulting lens to a function f : A x B -> A x B are equivalent to 
	 * extracting the A x B out of S, applying f to get a pair (a, b), and then setting
	 * the A in S to a using first followed by setting the B in S to b. 
	 * <br><br>
	 * May be used to focus on two different members of a structure as if they were a Pair.
	 * @param first
	 * @param second
	 * @return
	 */
	public static <S, A, B> SelfLens<S, Common.Pair<A, B>> split(Lens<S, S, A, A> first, Lens<S, S, B, B> second) {
		return (pairFunc) -> {
			return (container) -> {
				SelfLens<S, A> firstLens = SelfLens.of(first);
				SelfLens<S, B> secondLens = SelfLens.of(second);
				A a = firstLens.toGetter().apply(container);
				B b = secondLens.toGetter().apply(container);
				
				return secondLens.toSetter().apply(firstLens.toSetter().apply(container, a), b);
			};
		};
	}
	
	/**
	 * Given two functions which take a structure S and return references to fields of type A and B, 
	 * performs {@linkplain Lens#split(Lens, Lens)} as an in-place operation which mutates a structure of type S. 
	 * @param first
	 * @param second
	 * @return
	 */
	public static <S, A, B> SelfLens<S, Common.Pair<A, B>> split(Function<S, FieldReference<A>> first, Function<S, FieldReference<B>> second) {
		return split(Lens.of(first), Lens.of(second));
	}

	/**
	 * Returns a lens which focuses on two different members of type A within a structure of type S, where the order
	 * of operations is the same as in {@linkplain Lens#split(Lens, Lens)}.
	 * @param first
	 * @param second
	 * @return
	 */
	public static <S, A> SelfLens<S, A> bothOf(Lens<S, S, A, A> first, Lens<S, S, A, A> second) {
		return SelfLens.of(Lens.split(first, second).focus(Lens.pairBoth()));
	}
	/**
	 * {@linkplain Lens#bothOf(Lens, Lens)} specialized to perform mutations using references to a field obtained by "first"
	 * @param first
	 * @param second
	 * @return
	 */
	public static <S, A> SelfLens<S, A> bothOf(Function<S, FieldReference<A>> first, Lens<S, S, A, A> second) {
		return bothOf(Lens.of(first), second);
	}
	/**
	 * {@linkplain Lens#bothOf(Lens, Lens)} specialized to perform mutations using references to fields obtained by "first" and "second". 
	 * @param first
	 * @param second
	 * @return
	 */
	public static <S, A> SelfLens<S, A> bothOf(Function<S, FieldReference<A>> first, Function<S, FieldReference<A>> second) {
		return bothOf(Lens.of(first), Lens.of(second));
	}
	
	/**
	 * Given a function which takes a structure of type A and returns a field reference to a member of type B, 
	 * construct the (mutating) lens representation.
	 * @param fieldObtainer
	 * @return
	 */
	public static <A, B> FieldLens<A, B> of(Function<A, FieldReference<B>> fieldObtainer) {
		return new FieldLens<A, B>(fieldObtainer);
	}
	
	/**
	 * Specializes Lens<S, T, A, B> to the common special case where S=T, A=B to obtain the type SelfLens<S, A>. This often represents a method
	 * to update a member variable of type A within a structure of type S. 
	 * <br><br>
	 * Think of this interface as a type synonym with some additional operations, and nothing more.
	 * <br><br>
	 * A SelfLens<S, A> implementation does not need to mutate structures of type S, and pure implementations are often easier to reason about.
	 * However, for the sake of pragmatism, the {@linkplain FieldLens} variant is provided as a way to perform lens operations in-place.
	 * <br><br>
	 * The Haskell equivalent of this interface is Lens'
	 * @author bubble-07
	 *
	 * @param <S>
	 * @param <A>
	 */
	public interface SelfLens<S, A> extends Lens<S, S, A, A> {
		/**
		 * {@linkplain Lens#focus(Lens)} specialized to reflect the fact that the composition of two self-lenses is a self-lens.
		 * @param inner
		 * @return
		 */
		public default <B> SelfLens<S, B> focus(SelfLens<A, B> inner) {
			return (SelfLens<S, B>) ((Lens<S, S, A, A>) this).focus(inner);
		}
		/**
		 * FieldReference-provider sugar for {@linkplain SelfLens#focus(SelfLens)}.
		 * @param fieldObtainer
		 * @return
		 */
		public default <B> SelfLens<S, B> focus(Function<A, FieldReference<B>> fieldObtainer) {
			return this.focus(Lens.of(fieldObtainer));
		}
		/**
		 * Downcasts things that are SelfLenses, but aren't called that. If you make it possible to get ClassCastExceptions here, I'm calling Mom.
		 * @param in
		 * @return
		 */
		public static <S, A> SelfLens<S, A> of(Lens<S, S, A, A> in) {
			return (SelfLens<S, A>) in;
		}
		/**
		 * Terminal operation in a lensing chain returning a function that blindly sets a member in an enclosing structure
		 * to a constant value without considering its current value.
		 * @param val
		 * @return
		 */
		public default Function<S, S> set(A val) {
			return this.thenSet(val).apply(a -> a);
		}
		
		/**
		 * Intermediate lensing operation which will set a member to a given value before performing other lensing operations in the chain.
		 * @param val
		 * @return
		 */
		public default SelfLens<S, A> thenSet(A val) {
			return (Function<A, A> otherSetter) -> this.apply((ignored) -> val).andThen(this.apply(otherSetter));
		}
		
		/**
		 * Terminal operation in a lensing chain which returns the identity function on structures (modulo side effects)
		 * but may be used to perform the passed side-effecting action on the lensed member. 
		 * @param action
		 * @return
		 */
		public default Function<S, S> perform(Consumer<A> action) {
			return this.apply((a) -> {
				action.accept(a);
				return a;
			});
		}
		
		/**
		 * Terminal operation in a lensing chain which forgets everything a lens would tell you about how to set a member
		 * of a structure to yield a plain-old-getter (a function from the structure to the member).
		 * @return
		 */
		public default Function<S, A> toGetter() {
			return (S container) -> {
				Field<A> aVal = Field.initNull();
				this.apply((a) -> {
					aVal.set(a);
					return a;
				}).apply(container);
				return aVal.get();
			};
		}
		/**
		 * Terminal operation in a lensing chain which forgets everything a lens would tell you about getting a member
		 * out of a structure to yield a plain-old-setter (a function which takes a structure
		 * and a new value for the lensed member and returns the updated structure).
		 * @return
		 */
		public default BiFunction<S, A, S> toSetter() {
			return (S container, A memberVal) -> {
				return this.apply((ignored) -> memberVal).apply(container);
			};
		}
		
		/**
		 * Performs {@linkplain Lens#list()} using "cloner" to clone the enclosing structure to account for the possibility
		 * of multiple diverging mutations. "cloner" should be the identity if equality is taken to be ".equals",
		 * and should be s.t. any and all operations performed by lensing on a clone are completely independent
		 * from operations performed by lensing on the original. 
		 * @param cloner
		 * @return
		 */
		public default Lens<S, List<S>, A, List<A>> list(Function<S, S> cloner) {
			return (f) -> ((S s) -> {
				A member = this.toGetter().apply(s);
				return f.apply(member).stream()
						      .map((a) -> this.toSetter().apply(cloner.apply(s), a))
						      .collect(Collectors.toList());
			});
		}
	}
	
	/**
	 * 
	 * A version of {@linkplain SelfLens} that maintains a reference to a field of a structure and mutates in-place. 
	 * 
	 * @author bubble-07
	 *
	 * @param <A>
	 * @param <B>
	 */
	public class FieldLens<A, B> implements SelfLens<A, B> {
		private Function<A, FieldReference<B>> fieldObtainer;
		private FieldLens(Function<A, FieldReference<B>> fieldObtainer) {
			this.fieldObtainer = fieldObtainer;
		}
		
		@Override
		public Function<A, A> apply(Function<B, B> f) {
			return (container) -> {
				fieldObtainer.apply(container).transform(f);
				return container;
			};
		}
		@Override
		public Function<A, B> toGetter() {
			return (container) -> {
				return fieldObtainer.apply(container).get();
			};
		}
		@Override
		public BiFunction<A, B, A> toSetter() {
			return (container, member) -> {
				fieldObtainer.apply(container).set(member);
				return container;
			};
		}
		
	}
	
}
