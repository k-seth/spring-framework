/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.validation.beanvalidation;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MethodValidationAdapter}.
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 */
public class MethodValidationAdapterTests {

	private static final Person faustino1234 = new Person("Faustino1234", List.of("Working on Spring"));

	private static final Person cayetana6789 = new Person("Cayetana6789", List.of("  "));

	private static final Person baure4567 = new Person("Baure4567", List.of());

	private static final Course programming203 = new Course("Effective Unit Testing", Set.of(faustino1234), baure4567);

	private static final Course programming142 = new Course("Debugging Fundamentals", Set.of(baure4567), cayetana6789);


	private final MethodValidationAdapter validationAdapter = new MethodValidationAdapter();

	private final Locale originalLocale = Locale.getDefault();


	@BeforeEach
	void setDefaultLocaleToEnglish() {
		Locale.setDefault(Locale.ENGLISH);
	}

	@AfterEach
	void resetDefaultLocale() {
		Locale.setDefault(this.originalLocale);
	}

	@Test
	void validateArguments() {
		MyService target = new MyService();
		Method method = getMethod(target, "addStudent");

		testArgs(target, method, new Object[] {faustino1234, cayetana6789, 3}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(3);

			assertBeanResult(ex.getBeanResults().get(0), 0, "student", faustino1234, List.of("""
				Field error in object 'student' on field 'name': rejected value [Faustino1234]; \
				codes [Size.student.name,Size.name,Size.java.lang.String,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [student.name,name]; arguments []; default message [name],10,1]; \
				default message [size must be between 1 and 10]"""));
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();

			assertBeanResult(ex.getBeanResults().get(1), 1, "guardian", cayetana6789, List.of("""
				Field error in object 'guardian' on field 'name': rejected value [Cayetana6789]; \
				codes [Size.guardian.name,Size.name,Size.java.lang.String,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [guardian.name,name]; arguments []; default message [name],10,1]; \
				default message [size must be between 1 and 10]""", """
				Field error in object 'guardian' on field 'hobbies[0]': rejected value [  ]; \
				codes [NotBlank.guardian.hobbies[0],NotBlank.guardian.hobbies,NotBlank.hobbies[0],\
				NotBlank.hobbies,NotBlank.java.lang.String,NotBlank]; arguments \
				[org.springframework.context.support.DefaultMessageSourceResolvable: codes \
				[guardian.hobbies[0],hobbies[0]]; arguments []; default message [hobbies[0]]]; \
				default message [must not be blank]"""));
			assertThat(ex.getBeanResults().get(1).getContainer()).isNull();

			assertValueResult(ex.getValueResults().get(0), 2, 3, List.of("""
				org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [Max.myService#addStudent.degrees,Max.degrees,Max.int,Max]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [myService#addStudent.degrees,degrees]; arguments []; default message [degrees],2]; \
				default message [must be less than or equal to 2]"""));
		});
	}

	@Test
	void validateArgumentWithCustomObjectName() {
		MyService target = new MyService();
		Method method = getMethod(target, "addStudent");

		this.validationAdapter.setObjectNameResolver((param, value) -> "studentToAdd");

		testArgs(target, method, new Object[] {faustino1234, new Person("Joe", List.of()), 1}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertBeanResult(ex.getBeanResults().get(0), 0, "studentToAdd", faustino1234, List.of("""
				Field error in object 'studentToAdd' on field 'name': rejected value [Faustino1234]; \
				codes [Size.studentToAdd.name,Size.name,Size.java.lang.String,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [studentToAdd.name,name]; arguments []; default message [name],10,1]; \
				default message [size must be between 1 and 10]"""));
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();
		});
	}

	@Test
	void validateReturnValue() {
		MyService target = new MyService();

		testReturnValue(target, getMethod(target, "getIntValue"), 4, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertValueResult(ex.getValueResults().get(0), -1, 4, List.of("""
				org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [Min.myService#getIntValue,Min,Min.int]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [myService#getIntValue]; arguments []; default message [],5]; \
				default message [must be greater than or equal to 5]"""));
		});
	}

	@Test
	void validateReturnValueBean() {
		MyService target = new MyService();

		testReturnValue(target, getMethod(target, "getPerson"), faustino1234, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertBeanResult(ex.getBeanResults().get(0), -1, "person", faustino1234, List.of("""
				Field error in object 'person' on field 'name': rejected value [Faustino1234]; \
				codes [Size.person.name,Size.name,Size.java.lang.String,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [person.name,name]; arguments []; default message [name],10,1]; \
				default message [size must be between 1 and 10]"""));
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();
		});
	}

	@Test
	void validateListArgument() {
		MyService target = new MyService();
		Method method = getMethod(target, "addPeople");

		List<Person> arg = List.of(faustino1234, cayetana6789);
		testArgs(target, method, new Object[] {arg}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(2);

			int paramIndex = 0;
			String objectName = "people";
			List<ParameterErrors> results = ex.getBeanResults();

			assertBeanResult(results.get(0), paramIndex, objectName, faustino1234, List.of("""
				Field error in object 'people' on field 'name': rejected value [Faustino1234]; \
				codes [Size.people.name,Size.name,Size.java.lang.String,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [people.name,name]; arguments []; default message [name],10,1]; \
				default message [size must be between 1 and 10]"""));
			assertThat(results.get(0).getContainer()).isEqualTo(arg);

			assertBeanResult(results.get(1), paramIndex, objectName, cayetana6789, List.of("""
				Field error in object 'people' on field 'name': rejected value [Cayetana6789]; \
				codes [Size.people.name,Size.name,Size.java.lang.String,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [people.name,name]; arguments []; default message [name],10,1]; \
				default message [size must be between 1 and 10]""", """
				Field error in object 'people' on field 'hobbies[0]': rejected value [  ]; \
				codes [NotBlank.people.hobbies[0],NotBlank.people.hobbies,NotBlank.hobbies[0],\
				NotBlank.hobbies,NotBlank.java.lang.String,NotBlank]; arguments \
				[org.springframework.context.support.DefaultMessageSourceResolvable: codes \
				[people.hobbies[0],hobbies[0]]; arguments []; default message [hobbies[0]]]; \
				default message [must not be blank]"""));
			assertThat(results.get(0).getContainer()).isEqualTo(arg);
		});
	}

	@Test
	void validateSetArgument() {
		MyService target = new MyService();
		Method method = getMethod(target, "addPeople");

		Set<Person> arg = Set.of(faustino1234, cayetana6789);
		testArgs(target, method, new Object[] {arg}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(2);

			int paramIndex = 0;
			String objectName = "people";
			List<ParameterErrors> results = ex.getBeanResults();

			assertThat(results).satisfiesExactlyInAnyOrder(
				result -> {
					assertBeanResult(result, paramIndex, objectName, faustino1234, List.of("""
						Field error in object 'people' on field 'name': rejected value [Faustino1234]; \
						codes [Size.people.name,Size.name,Size.java.lang.String,Size]; \
						arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
						codes [people.name,name]; arguments []; default message [name],10,1]; \
						default message [size must be between 1 and 10]"""));
					assertThat(result.getContainer()).isEqualTo(arg);
				},
				result -> {
					assertBeanResult(result, paramIndex, objectName, cayetana6789, List.of("""
						Field error in object 'people' on field 'name': rejected value [Cayetana6789]; \
						codes [Size.people.name,Size.name,Size.java.lang.String,Size]; \
						arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
						codes [people.name,name]; arguments []; default message [name],10,1]; \
						default message [size must be between 1 and 10]""", """
						Field error in object 'people' on field 'hobbies[0]': rejected value [  ]; \
						codes [NotBlank.people.hobbies[0],NotBlank.people.hobbies,NotBlank.hobbies[0],\
						NotBlank.hobbies,NotBlank.java.lang.String,NotBlank]; arguments \
						[org.springframework.context.support.DefaultMessageSourceResolvable: codes \
						[people.hobbies[0],hobbies[0]]; arguments []; default message [hobbies[0]]]; \
						default message [must not be blank]"""));
					assertThat(result.getContainer()).isEqualTo(arg);
				}
			);
		});
	}

	// Problem 1 - Cascaded validation produces JSR error on some paths
	@Test
	void problemOne_validateNestedArgument() {
		MyService target = new MyService();
		Method method = getMethod(target, "registerCourse");

		// Fails attempting to validate as path professor.name is not valid for type Person
		testArgs(target, method, new Object[] {programming142}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertBeanResult(ex.getBeanResults().get(0), 0, "course", faustino1234, List.of("""
				Field error in object 'course' on field 'professor.name': rejected value [Faustino1234]; \
				codes [Size.course.professor.name,Size.professor.name,Size.name,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [course.professor.name,professor.name]; arguments []; default message [professor.name],10,1]; \
				default message [size must be between 1 and 10]"""));
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();
		});
	}

	@Test
	void problemOne_validateNestedReturnValueBean() {
		MyService target = new MyService();

		// Fails attempting to validate as path professor.name is not valid for type Person
		testReturnValue(target, getMethod(target, "getCourse"), programming142, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertBeanResult(ex.getBeanResults().get(0), -1, "course", faustino1234, List.of("""
				Field error in object 'course' on field 'professor.name': rejected value [Faustino1234]; \
				codes [Size.course.professor.name,Size.professor.name,Size.name,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [course.professor.name,professor.name]; arguments []; default message [professor.name],10,1]; \
				default message [size must be between 1 and 10]"""));
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();
		});
	}


	// Problem 2 - Container argument incorrect when validating non-field object error
	@Test
	void problemTwo_validateListArgument() {
		MyService target = new MyService();
		Method method = getMethod(target, "setHobbiesList");

		List<String> arg = List.of(" ", "Developing", "");
		testArgs(target, method, new Object[] {arg}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(2);

			int paramIndex = 0;
			String objectName = "hobbies";
			List<ParameterErrors> results = ex.getBeanResults();

			assertThat(results).satisfiesExactlyInAnyOrder(
				result -> {
					// Fails argument assertion as the argument is set as the service class
					assertBeanResult(result, paramIndex, objectName, " ", List.of("""
						Error in object 'hobbies': \
						codes [NotBlank.hobbies,NotBlank]; \
						arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
						codes [hobbies]; arguments []; default message []]; \
						default message [must not be blank]"""));
					assertThat(result.getContainer()).isEqualTo(arg);
				},
				result -> {
					// Fails argument assertion as the argument is set as the service class
					assertBeanResult(result, paramIndex, objectName, "", List.of("""
						Error in object 'hobbies': \
						codes [NotBlank.hobbies,NotBlank]; \
						arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
						codes [hobbies]; arguments []; default message []]; \
						default message [must not be blank]"""));
					assertThat(result.getContainer()).isEqualTo(arg);
				}
			);
		});
	}

	@Test
	void problemTwo_validateSetArgument() {
		MyService target = new MyService();
		Method method = getMethod(target, "setHobbiesSet");

		Set<String> arg = Set.of(" ", "Developing", "");
		testArgs(target, method, new Object[] {arg}, ex -> {

			// Fails result count as all violations are mapped to the service class
			assertThat(ex.getAllValidationResults()).hasSize(2);

			int paramIndex = 0;
			String objectName = "hobbies";
			List<ParameterErrors> results = ex.getBeanResults();

			assertThat(results).satisfiesExactlyInAnyOrder(
					result -> {
						assertBeanResult(result, paramIndex, objectName, " ", List.of("""
							Error in object 'hobbies': \
							codes [NotBlank.hobbies,NotBlank]; \
							arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
							codes [hobbies]; arguments []; default message []]; \
							default message [must not be blank]"""));
						assertThat(result.getContainer()).isEqualTo(arg);
					},
					result -> {
						assertBeanResult(result, paramIndex, objectName, "", List.of("""
							Error in object 'hobbies': \
							codes [NotBlank.hobbies,NotBlank]; \
							arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
							codes [people.name,name]; arguments []; default message []]; \
							default message [must not be blank]"""));
						assertThat(result.getContainer()).isEqualTo(arg);
					}
			);
		});
	}


	// Problem 3 - Bean result container incorrectly set on nested validation
	@Test
	void problemThree_validateNestedArgument() {
		MyService target = new MyService();
		Method method = getMethod(target, "registerCourse");

		testArgs(target, method, new Object[] {programming203}, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertBeanResult(ex.getBeanResults().get(0), 0, "course", faustino1234, List.of("""
				Field error in object 'course' on field 'students[].name': rejected value [Faustino1234]; \
				codes [Size.course.students[].name,Size.course.students.name,Size.students[].name,Size.students.name,Size.name,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [course.students[].name,students[].name]; arguments []; default message [students[].name],10,1]; \
				default message [size must be between 1 and 10]"""));
			// Fails as the container type is set due because leaf bean != arg
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();
		});
	}

	@Test
	void problemThree_validateNestedReturnValueBean() {
		MyService target = new MyService();

		testReturnValue(target, getMethod(target, "getCourse"), programming203, ex -> {

			assertThat(ex.getAllValidationResults()).hasSize(1);

			assertBeanResult(ex.getBeanResults().get(0), -1, "course", faustino1234, List.of("""
				Field error in object 'course' on field 'students[].name': rejected value [Faustino1234]; \
				codes [Size.course.students[].name,Size.course.students.name,Size.students[].name,Size.students.name,Size.name,Size]; \
				arguments [org.springframework.context.support.DefaultMessageSourceResolvable: \
				codes [course.students[].name,students[].name]; arguments []; default message [students[].name],10,1]; \
				default message [size must be between 1 and 10]"""));
			// Fails as the container type is set due because leaf bean != arg
			assertThat(ex.getBeanResults().get(0).getContainer()).isNull();
		});
	}

	private void testArgs(Object target, Method method, Object[] args, Consumer<MethodValidationResult> consumer) {
		consumer.accept(this.validationAdapter.validateArguments(target, method, null, args, new Class<?>[0]));
	}

	private void testReturnValue(Object target, Method method, @Nullable Object value, Consumer<MethodValidationResult> consumer) {
		consumer.accept(this.validationAdapter.validateReturnValue(target, method, null, value, new Class<?>[0]));
	}

	private static void assertBeanResult(
			ParameterErrors errors, int parameterIndex, String objectName, @Nullable Object argument,
			List<String> objectErrors) {

		assertThat(errors.getMethodParameter().getParameterIndex()).isEqualTo(parameterIndex);
		assertThat(errors.getObjectName()).isEqualTo(objectName);
		assertThat(errors.getArgument()).isSameAs(argument);

		assertThat(errors.getAllErrors())
				.extracting(ObjectError::toString)
				.containsExactlyInAnyOrderElementsOf(objectErrors);
	}

	private static void assertValueResult(
			ParameterValidationResult result, int parameterIndex, Object argument, List<String> errors) {

		assertThat(result.getMethodParameter().getParameterIndex()).isEqualTo(parameterIndex);
		assertThat(result.getArgument()).isEqualTo(argument);
		assertThat(result.getResolvableErrors())
				.extracting(MessageSourceResolvable::toString)
				.containsExactlyInAnyOrderElementsOf(errors);
	}

	private static Method getMethod(Object target, String methodName) {
		return ClassUtils.getMethod(target.getClass(), methodName, (Class<?>[]) null);
	}


	@SuppressWarnings("unused")
	private static class MyService {

		public void addStudent(@Valid Person student, @Valid Person guardian, @Max(2) int degrees) {
		}

		@Min(5)
		public int getIntValue() {
			throw new UnsupportedOperationException();
		}

		@Valid
		public Person getPerson() {
			throw new UnsupportedOperationException();
		}

		public void addPeople(@Valid Collection<Person> people) {
		}

		public void registerCourse(@Valid Course course) {
		}

		@Valid
		public Course getCourse() {
			throw new UnsupportedOperationException();
		}

		public void setHobbiesSet(@Valid Set<@NotBlank String> hobbies) {
		}

		public void setHobbiesList(@Valid List<@NotBlank String> hobbies) {
		}

	}

	@SuppressWarnings("unused")
	private record Course(@NotBlank String title, @Valid Set<Person> students, @Valid Person professor) {
	}

	@SuppressWarnings("unused")
	private record Person(@Size(min = 1, max = 10) String name, List<@NotBlank String> hobbies) {
	}

}
