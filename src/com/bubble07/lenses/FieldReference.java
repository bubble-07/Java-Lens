package com.bubble07.lenses;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Interface representing a reference to a field of an object through its setters/getters. 
 * @author bubble-07
 *
 * @param <T>
 */
public interface FieldReference<T> {
	public void set(T in);
	public T get();
	
	/**
	 * Explicitly construct a {@linkplain FieldReference} from a setter and a getter.
	 * @param set
	 * @param get
	 * @return
	 */
	public default FieldReference<T> of(Consumer<T> set, Supplier<T> get) {
		return new FieldReference<T>() {
			@Override
			public void set(T in) {
				set.accept(in);
			}
			@Override
			public T get() {
				return get.get();
			}
		};
	}
	
	/**
	 * Get something of type T from a field, transform it using a function : T -> T, and set the field to the result of the application.
	 * @param action
	 * @return
	 */
	public default T transform(Function<T, T> action) {
		T result = action.apply(get());
		set(result);
		return result;
	}
}
