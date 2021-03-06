/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class JavaPsiTest extends LightCodeInsightFixtureTestCase {
  void testEmptyImportList() {
    assert configureFile("").importList != null
    assert configureFile("class C { }").importList != null
    assert configureFile("module M { }").importList != null
  }

  void testModuleInfo() {
    def file = configureFile("module M { }")
    assert file.packageName == ""
    def module = file.moduleDeclaration
    assert module != null
    assert module.name == "M"
    assert module.modifierList != null
  }

  private PsiJavaFile configureFile(String text) {
    myFixture.configureByText("a.java", text) as PsiJavaFile
  }
}