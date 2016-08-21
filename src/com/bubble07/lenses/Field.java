package com.bubble07.lenses;

/**
 * Basic implementation of a {@linkplain FieldReference} which wraps a plain-old java member variable.
 * If you need fancier setters/getters, consider using the more general {@linkplain FieldReference} instead.
 * @author bubble-07
 *
 * @param <T>
 */
public class Field<T> implements FieldReference<T> {
	private T val;
	private Field(T initVal) {
		this.val = initVal;
	}
	
	/**
	 * @return A Field wrapping an initially-null value
	 */
	public static <T> Field<T> initNull() {
		return new Field<T>(null);
	}
	
	/**
	 * @param initVal
	 * @return A Field wrapping initVal
	 */
	public static <T> Field<T> withDefault(T initVal) {
		return new Field<T>(initVal);
	}
	
	@Override
	public void set(T in) {
		this.val = in;
	}
	@Override
	public T get() {
		return this.val;
	}

}
