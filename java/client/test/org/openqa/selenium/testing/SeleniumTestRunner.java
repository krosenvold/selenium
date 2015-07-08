// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.testing;

import static org.openqa.selenium.testing.DevMode.isInDevMode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.openqa.selenium.NeedsFreshDriver;
import org.openqa.selenium.NoDriverAfterTest;
import org.openqa.selenium.testing.Ignore.Driver;
import org.openqa.selenium.testing.drivers.Browser;
import org.openqa.selenium.testing.drivers.SauceDriver;
import org.openqa.selenium.testing.drivers.TestIgnorance;

import com.google.common.base.Throwables;

public class SeleniumTestRunner extends BlockJUnit4ClassRunner {

  private Browser browser;
  private TestIgnorance ignorance;

  /**
   * Creates a BlockJUnit4ClassRunner to run {@code klass}
   *
   * @param klass The class under test
   * @throws org.junit.runners.model.InitializationError
   *          if the test class is malformed.
   */
  public SeleniumTestRunner(Class<?> klass) throws InitializationError {
    super(klass);

    browser = Browser.detect();
    if (browser == null && isInDevMode()) {
      browser = Browser.ff;
    }

    ignorance = new TestIgnorance(browser);
  }

  @Override
  protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
    Object test = null;
    Description description = describeChild(method);
    try {
      test = new ReflectiveCallable() {
        @Override
        protected Object runReflectiveCall() throws Throwable {
          return createTest();
        }
      }.run();
    } catch (Throwable e) {
      runLeaf(new Fail(e), description, notifier);
    }

    if (ignorance.isIgnored(method, test)) {
      notifier.fireTestIgnored(description);
    } else {
      runLeaf(methodBlock(method, test), description, notifier);
    }
  }

  @SuppressWarnings("deprecation")
  private Statement methodBlock(FrameworkMethod method, Object test) {
    Statement statement = methodInvoker(method, test);
    statement = possiblyExpectingExceptions(method, test, statement);
    statement = withPotentialTimeout(method, test, statement);
    statement = withBefores(method, test, statement);
    statement = withAfters(method, test, statement);
    statement = withRules(method, test, statement);
    if (test instanceof JUnit4TestBase) {
      JUnit4TestBase base = (JUnit4TestBase) test;
      statement = withNoDriverAfterTest(method, statement);
      statement = withFreshDriver(method, base, statement);
      statement = coveringUpSauceErrors(statement);
    }
    statement = withNotYetImplemented(method, statement);
    return statement;
  }

  private Statement coveringUpSauceErrors(final Statement statement) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          statement.evaluate();
        } catch (Throwable t) {
          dealWithSauceFailureIfNecessary(t);
          // retry if we got a 'sauce' failure
          statement.evaluate();
        }
      }
    };
  }

  private void dealWithSauceFailureIfNecessary(Throwable t) {
    if (t.getMessage() != null
        && (t.getMessage().contains("sauce") || t.getMessage().contains("Sauce"))) {
      JUnit4TestBase.removeDriver();
      try {
        JUnit4TestBase.actuallyCreateDriver();
      } catch (Exception e) {
        throw new RuntimeException("Exception creating driver, after Sauce-detected exception", e);
      }
    } else {
      throw Throwables.propagate(t);
    }
  }

  private Statement withFreshDriver(
      FrameworkMethod method, final JUnit4TestBase test, final Statement statement) {

    NeedsFreshDriver annotation = method.getAnnotation(NeedsFreshDriver.class);
    if (annotation == null) {
      return statement;
    }

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        JUnit4TestBase.removeDriver();  // bletch
        test.createDriver();
        statement.evaluate();
      }
    };
  }

  private Statement withNoDriverAfterTest(
      FrameworkMethod method, final Statement statement) {

    NoDriverAfterTest annotation = method.getAnnotation(NoDriverAfterTest.class);
    if (annotation == null) {
      return statement;
    }

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          statement.evaluate();
        } finally {
          JUnit4TestBase.removeDriver();  // bletch
        }
      }
    };
  }


  private Statement withRules(FrameworkMethod method, Object target, Statement statement) {
    try {
      Method withRules = BlockJUnit4ClassRunner.class.getDeclaredMethod(
          "withRules", FrameworkMethod.class, Object.class, Statement.class);
      withRules.setAccessible(true);

      return (Statement) withRules.invoke(this, method, target, statement);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw Throwables.propagate(e);
    }
  }

  protected Statement withNotYetImplemented(final FrameworkMethod method, final Statement statement) {
    if (!isNotYetImplemented(method)) {
      return statement;
    }

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Exception toBeThrown = null;
        try {
          statement.evaluate();
          toBeThrown = new Exception(method.getDeclaringClass().getSimpleName() + '.' + method.getName()
              + " is marked as not yet implemented with " + browser + " but already works!");
        }
        catch (final Throwable e) {
          // expected
        }
        if (toBeThrown != null) {
          throw toBeThrown;
        }
      }
    };
  }

  protected boolean isNotYetImplemented(final FrameworkMethod method) {
    final NotYetImplemented notYetImplementedBrowsers = method.getAnnotation(NotYetImplemented.class);
    boolean isNotYetImplemented = false;
    if (notYetImplementedBrowsers != null) {
      for (Driver driver : notYetImplementedBrowsers.value()) {
        if (!isNotYetImplemented) {
          switch (driver) {
          case ALL:
            isNotYetImplemented = true;
            break;

          case CHROME:
            isNotYetImplemented = browser == Browser.chrome;
            break;

          case FIREFOX:
            if (!Boolean.getBoolean("webdriver.firefox.marionette")) {
              isNotYetImplemented = browser == Browser.ff;
            }
            break;

          case HTMLUNIT:
            isNotYetImplemented = browser == Browser.htmlunit || browser == Browser.htmlunit_js;
            break;

          case IE:
            isNotYetImplemented = browser == Browser.ie;
            break;

          case MARIONETTE:
            if (Boolean.getBoolean("webdriver.firefox.marionette")) {
              isNotYetImplemented = browser == Browser.ff;
            }
            break;

          case PHANTOMJS:
            isNotYetImplemented = browser == Browser.phantomjs;
            break;

          case REMOTE:
            isNotYetImplemented = Boolean.getBoolean("selenium.browser.remote") || SauceDriver.shouldUseSauce();
            break;

          case SAFARI:
            isNotYetImplemented = browser == Browser.safari;
            break;

          default:
            throw new RuntimeException("Cannot determine driver");
          }
        }
      }
    }
    return isNotYetImplemented;
  }
}
