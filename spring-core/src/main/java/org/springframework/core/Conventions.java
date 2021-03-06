/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Iterator;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Provides methods to support various naming and other conventions used
 * throughout the framework. Mainly for internal use within the framework.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 2.0
 */
public final class Conventions {

	/**
	 * Suffix added to names when using arrays.
	 */
	private static final String PLURAL_SUFFIX = "List";


	private Conventions() {
	}


	/**
	 * 确定给定 Object 的变量名
	 *
	 * Determine the conventional variable name for the supplied {@code Object}
	 * based on its concrete type. The convention used is to return the
	 * un-capitalized short name of the {@code Class}, according to JavaBeans
	 * property naming rules.
	 * <p>For example:<br>
	 * {@code com.myapp.Product} becomes {@code "product"}<br>
	 * {@code com.myapp.MyProduct} becomes {@code "myProduct"}<br>
	 * {@code com.myapp.UKProduct} becomes {@code "UKProduct"}<br>
	 * <p>For arrays the pluralized version of the array component type is used.
	 * For {@code Collection}s an attempt is made to 'peek ahead' to determine
	 * the component type and return its pluralized version.
	 * @param value the value to generate a variable name for
	 * @return the generated variable name
	 */
	public static String getVariableName(Object value) {
		Assert.notNull(value, "Value must not be null");
		Class<?> valueClass;
		boolean pluralize = false;

		// 如果是数组类型
		if (value.getClass().isArray()) {
			// 获取组件类型
			// Integer[] 则返回 Integer.class
			valueClass = value.getClass().getComponentType();
			pluralize = true;
		}
		// 如果是 Collection 类型
		else if (value instanceof Collection) {
			Collection<?> collection = (Collection<?>) value;
			// 集合中元素个数为 0，直接抛异常
			if (collection.isEmpty()) {
				throw new IllegalArgumentException(
						"Cannot generate variable name for an empty Collection");
			}
			// 获取第一个元素
			Object valueToCheck = peekAhead(collection);
			// 获取元素的 class 类型
			valueClass = getClassForValue(valueToCheck);
			pluralize = true;
		}
		else {
			valueClass = getClassForValue(value);
		}

		// 获取类名
		// 一般类 返回类名首字母小写
		// 内部类则是外部类名+内部类名 大写
		String name = ClassUtils.getShortNameAsProperty(valueClass);
		// 如果是 数组或者 Collection 在最后还要加上 List
		return (pluralize ? pluralize(name) : name);
	}

	/**
	 * Determine the conventional variable name for the given parameter taking
	 * the generic collection type, if any, into account.
	 * <p>As of 5.0 this method supports reactive types:<br>
	 * {@code Mono<com.myapp.Product>} becomes {@code "productMono"}<br>
	 * {@code Flux<com.myapp.MyProduct>} becomes {@code "myProductFlux"}<br>
	 * {@code Observable<com.myapp.MyProduct>} becomes {@code "myProductObservable"}<br>
	 * @param parameter the method or constructor parameter
	 * @return the generated variable name
	 */
	public static String getVariableNameForParameter(MethodParameter parameter) {
		Assert.notNull(parameter, "MethodParameter must not be null");
		Class<?> valueClass;
		boolean pluralize = false;
		String reactiveSuffix = "";

		if (parameter.getParameterType().isArray()) {
			valueClass = parameter.getParameterType().getComponentType();
			pluralize = true;
		}
		else if (Collection.class.isAssignableFrom(parameter.getParameterType())) {
			valueClass = ResolvableType.forMethodParameter(parameter).asCollection().resolveGeneric();
			if (valueClass == null) {
				throw new IllegalArgumentException(
						"Cannot generate variable name for non-typed Collection parameter type");
			}
			pluralize = true;
		}
		else {
			valueClass = parameter.getParameterType();
			ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(valueClass);
			if (adapter != null && !adapter.getDescriptor().isNoValue()) {
				reactiveSuffix = ClassUtils.getShortName(valueClass);
				valueClass = parameter.nested().getNestedParameterType();
			}
		}

		String name = ClassUtils.getShortNameAsProperty(valueClass);
		return (pluralize ? pluralize(name) : name + reactiveSuffix);
	}

	/**
	 * Determine the conventional variable name for the return type of the
	 * given method, taking the generic collection type, if any, into account.
	 * @param method the method to generate a variable name for
	 * @return the generated variable name
	 */
	public static String getVariableNameForReturnType(Method method) {
		return getVariableNameForReturnType(method, method.getReturnType(), null);
	}

	/**
	 * Determine the conventional variable name for the return type of the given
	 * method, taking the generic collection type, if any, into account, falling
	 * back on the given actual return value if the method declaration is not
	 * specific enough, e.g. {@code Object} return type or untyped collection.
	 * @param method the method to generate a variable name for
	 * @param value the return value (may be {@code null} if not available)
	 * @return the generated variable name
	 */
	public static String getVariableNameForReturnType(Method method, @Nullable Object value) {
		return getVariableNameForReturnType(method, method.getReturnType(), value);
	}

	/**
	 * Determine the conventional variable name for the return type of the given
	 * method, taking the generic collection type, if any, into account, falling
	 * back on the given return value if the method declaration is not specific
	 * enough, e.g. {@code Object} return type or untyped collection.
	 * <p>As of 5.0 this method supports reactive types:<br>
	 * {@code Mono<com.myapp.Product>} becomes {@code "productMono"}<br>
	 * {@code Flux<com.myapp.MyProduct>} becomes {@code "myProductFlux"}<br>
	 * {@code Observable<com.myapp.MyProduct>} becomes {@code "myProductObservable"}<br>
	 * @param method the method to generate a variable name for
	 * @param resolvedType the resolved return type of the method
	 * @param value the return value (may be {@code null} if not available)
	 * @return the generated variable name
	 */
	public static String getVariableNameForReturnType(Method method, Class<?> resolvedType, @Nullable Object value) {
		Assert.notNull(method, "Method must not be null");

		// 如果是 Object 类型，直接解析
		if (Object.class == resolvedType) {
			if (value == null) {
				throw new IllegalArgumentException(
						"Cannot generate variable name for an Object return type with null value");
			}
			return getVariableName(value);
		}

		Class<?> valueClass;
		boolean pluralize = false;
		String reactiveSuffix = "";

		// 数组类型
		if (resolvedType.isArray()) {
			valueClass = resolvedType.getComponentType();
			pluralize = true;
		}
		// Collection 类型
		else if (Collection.class.isAssignableFrom(resolvedType)) {
			valueClass = ResolvableType.forMethodReturnType(method).asCollection().resolveGeneric();
			if (valueClass == null) {
				if (!(value instanceof Collection)) {
					throw new IllegalArgumentException("Cannot generate variable name " +
							"for non-typed Collection return type and a non-Collection value");
				}
				Collection<?> collection = (Collection<?>) value;
				if (collection.isEmpty()) {
					throw new IllegalArgumentException("Cannot generate variable name " +
							"for non-typed Collection return type and an empty Collection value");
				}
				Object valueToCheck = peekAhead(collection);
				valueClass = getClassForValue(valueToCheck);
			}
			pluralize = true;
		}
		else {
			valueClass = resolvedType;
			ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(valueClass);
			if (adapter != null && !adapter.getDescriptor().isNoValue()) {
				reactiveSuffix = ClassUtils.getShortName(valueClass);
				valueClass = ResolvableType.forMethodReturnType(method).getGeneric().toClass();
			}
		}

		// 获取名字
		String name = ClassUtils.getShortNameAsProperty(valueClass);
		return (pluralize ? pluralize(name) : name + reactiveSuffix);
	}

	/**
	 * Convert {@code String}s in attribute name format (e.g. lowercase, hyphens
	 * separating words) into property name format (camel-case). For example
	 * {@code transaction-manager} becomes {@code "transactionManager"}.
	 */
	public static String attributeNameToPropertyName(String attributeName) {
		Assert.notNull(attributeName, "'attributeName' must not be null");
		if (!attributeName.contains("-")) {
			return attributeName;
		}
		char[] chars = attributeName.toCharArray();
		char[] result = new char[chars.length -1]; // not completely accurate but good guess
		int currPos = 0;
		boolean upperCaseNext = false;
		for (char c : chars) {
			if (c == '-') {
				upperCaseNext = true;
			}
			else if (upperCaseNext) {
				result[currPos++] = Character.toUpperCase(c);
				upperCaseNext = false;
			}
			else {
				result[currPos++] = c;
			}
		}
		return new String(result, 0, currPos);
	}

	/**
	 * Return an attribute name qualified by the given enclosing {@link Class}.
	 * For example the attribute name '{@code foo}' qualified by {@link Class}
	 * '{@code com.myapp.SomeClass}' would be '{@code com.myapp.SomeClass.foo}'
	 */
	public static String getQualifiedAttributeName(Class<?> enclosingClass, String attributeName) {
		Assert.notNull(enclosingClass, "'enclosingClass' must not be null");
		Assert.notNull(attributeName, "'attributeName' must not be null");
		return enclosingClass.getName() + '.' + attributeName;
	}


	/**
	 * Determine the class to use for naming a variable containing the given value.
	 * <p>Will return the class of the given value, except when encountering a
	 * JDK proxy, in which case it will determine the 'primary' interface
	 * implemented by that proxy.
	 * @param value the value to check
	 * @return the class to use for naming a variable
	 */
	private static Class<?> getClassForValue(Object value) {
		// 获取当前类型的 class type
		Class<?> valueClass = value.getClass();
		// 判断是否是 代理类  JDK 动态代理
		if (Proxy.isProxyClass(valueClass)) {
			// 得到接口，因为是 JDK 动态代理，肯定会实现接口
			Class<?>[] ifcs = valueClass.getInterfaces();
			for (Class<?> ifc : ifcs) {
				// 找到第一个不是 Java 语言相关的接口
				if (!ClassUtils.isJavaLanguageInterface(ifc)) {
					// 返回该类型
					return ifc;
				}
			}
		}
		// 普通类
		else if (valueClass.getName().lastIndexOf('$') != -1 && valueClass.getDeclaringClass() == null) {
			// '$' in the class name but no inner class -
			// assuming it's a special subclass (e.g. by OpenJPA)
			valueClass = valueClass.getSuperclass();
		}
		return valueClass;
	}

	/**
	 * Pluralize the given name.
	 */
	private static String pluralize(String name) {
		return name + PLURAL_SUFFIX;
	}

	/**
	 * 返回集合中的第一个元素
	 *
	 * Retrieve the {@code Class} of an element in the {@code Collection}.
	 * The exact element for which the {@code Class} is retrieved will depend
	 * on the concrete {@code Collection} implementation.
	 */
	private static <E> E peekAhead(Collection<E> collection) {
		Iterator<E> it = collection.iterator();
		if (!it.hasNext()) {
			throw new IllegalStateException(
					"Unable to peek ahead in non-empty collection - no element found");
		}
		E value = it.next();
		if (value == null) {
			throw new IllegalStateException(
					"Unable to peek ahead in non-empty collection - only null element found");
		}
		return value;
	}

}
