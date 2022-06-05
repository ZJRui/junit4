package org.junit.internal.builders;

import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.lang.reflect.Modifier;


/**
 * The {@code AnnotatedBuilder} is a strategy for constructing runners for test class that have been annotated with the
 * {@code @RunWith} annotation. All tests within this class will be executed using the runner that was specified within
 * the annotation.
 * <p>
 * If a runner supports inner member classes, the member classes will inherit the runner from the enclosing class, e.g.:
 * <pre>
 * &#064;RunWith(MyRunner.class)
 * public class MyTest {
 *     // some tests might go here
 *
 *     public class MyMemberClass {
 *         &#064;Test
 *         public void thisTestRunsWith_MyRunner() {
 *             // some test logic
 *         }
 *
 *         // some more tests might go here
 *     }
 *
 *     &#064;RunWith(AnotherRunner.class)
 *     public class AnotherMemberClass {
 *         // some tests might go here
 *
 *         public class DeepInnerClass {
 *             &#064;Test
 *             public void thisTestRunsWith_AnotherRunner() {
 *                 // some test logic
 *             }
 *         }
 *
 *         public class DeepInheritedClass extends SuperTest {
 *             &#064;Test
 *             public void thisTestRunsWith_SuperRunner() {
 *                 // some test logic
 *             }
 *         }
 *     }
 * }
 *
 * &#064;RunWith(SuperRunner.class)
 * public class SuperTest {
 *     // some tests might go here
 * }
 * </pre>
 * The key points to note here are:
 * <ul>
 *     <li>If there is no RunWith annotation, no runner will be created.</li>
 *     <li>The resolve step is inside-out, e.g. the closest RunWith annotation wins</li>
 *     <li>RunWith annotations are inherited and work as if the class was annotated itself.</li>
 *     <li>The default JUnit runner does not support inner member classes,
 *         so this is only valid for custom runners that support inner member classes.</li>
 *     <li>Custom runners with support for inner classes may or may not support RunWith annotations for member
 *         classes. Please refer to the custom runner documentation.</li>
 * </ul>
 *
 * @see org.junit.runners.model.RunnerBuilder
 * @see org.junit.runner.RunWith
 * @since 4.0
 */
public class AnnotatedBuilder extends RunnerBuilder {
    private static final String CONSTRUCTOR_ERROR_FORMAT = "Custom runner class %s should have a public constructor with signature %s(Class testClass)";

    private final RunnerBuilder suiteBuilder;

    public AnnotatedBuilder(RunnerBuilder suiteBuilder) {
        this.suiteBuilder = suiteBuilder;
    }

    @Override
    public Runner runnerForClass(Class<?> testClass) throws Exception {
        /**
         *
         * buildTestContext:103, SpringBootTestContextBootstrapper (org.springframework.boot.test.context)
         * <init>:137, TestContextManager (org.springframework.test.context)
         * <init>:122, TestContextManager (org.springframework.test.context)
         * createTestContextManager:151, SpringJUnit4ClassRunner (org.springframework.test.context.junit4)
         * <init>:142, SpringJUnit4ClassRunner (org.springframework.test.context.junit4)
         * <init>:49, SpringRunner (org.springframework.test.context.junit4)
         * newInstance0:-1, NativeConstructorAccessorImpl (sun.reflect)
         * newInstance:62, NativeConstructorAccessorImpl (sun.reflect)
         * newInstance:45, DelegatingConstructorAccessorImpl (sun.reflect)
         * newInstance:423, Constructor (java.lang.reflect)
         *
         *
         * buildRunner:104, AnnotatedBuilder (org.junit.internal.builders)
         * runnerForClass:86, AnnotatedBuilder (org.junit.internal.builders)  ---->?????? AnnotatedBuilder?runnerForClass?
         * safeRunnerForClass:70, RunnerBuilder (org.junit.runners.model)
         * runnerForClass:37, AllDefaultPossibilitiesBuilder (org.junit.internal.builders)
         * safeRunnerForClass:70, RunnerBuilder (org.junit.runners.model)
         * createRunner:28, ClassRequest (org.junit.internal.requests)
         * getRunner:19, MemoizingRequest (org.junit.internal.requests)
         * getRunner:36, FilterRequest (org.junit.internal.requests)
         * startRunnerWithArgs:50, JUnit4IdeaTestRunner (com.intellij.junit4)
         * execute:38, IdeaTestRunner$Repeater$1 (com.intellij.rt.junit)
         * repeat:11, TestsRepeater (com.intellij.rt.execution.junit)
         * startRunnerWithArgs:35, IdeaTestRunner$Repeater (com.intellij.rt.junit)
         * prepareStreamsAndStart:235, JUnitStarter (com.intellij.rt.junit)
         * main:54, JUnitStarter (com.intellij.rt.junit)
         *
         *
         * runnerForClass?????TestClass??RunWith?????RunWith?????Runner
         *
         *
         */
        for (Class<?> currentTestClass = testClass; currentTestClass != null;
             currentTestClass = getEnclosingClassForNonStaticMemberClass(currentTestClass)) {
            RunWith annotation = currentTestClass.getAnnotation(RunWith.class);
            if (annotation != null) {
                //?????????Runner??
                return buildRunner(annotation.value(), testClass);
            }
        }

        return null;
    }

    private Class<?> getEnclosingClassForNonStaticMemberClass(Class<?> currentTestClass) {
        if (currentTestClass.isMemberClass() && !Modifier.isStatic(currentTestClass.getModifiers())) {
            return currentTestClass.getEnclosingClass();
        } else {
            return null;
        }
    }

    public Runner buildRunner(Class<? extends Runner> runnerClass,
            Class<?> testClass) throws Exception {
        try {
            return runnerClass.getConstructor(Class.class).newInstance(testClass);
        } catch (NoSuchMethodException e) {
            try {
                return runnerClass.getConstructor(Class.class,
                        RunnerBuilder.class).newInstance(testClass, suiteBuilder);
            } catch (NoSuchMethodException e2) {
                String simpleName = runnerClass.getSimpleName();
                throw new InitializationError(String.format(
                        CONSTRUCTOR_ERROR_FORMAT, simpleName, simpleName));
            }
        }
    }
}