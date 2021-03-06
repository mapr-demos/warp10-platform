//
//   Copyright 2016  Cityzen Data
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.binary;

/**
 * Conditional OR taking either:
 * - two operands on top of the stack.
 * - a list of booleans or boolean-returning-macros.
 * This class implements short-circuit evaluation (https://en.wikipedia.org/wiki/Short-circuit_evaluation).
 */
public class CondOR extends CondShortCircuit {

  public CondOR(String name) {
    super(name, true);
  }

  @Override
  public boolean operator(boolean bool1, boolean bool2) {
    return bool1 || bool2;
  }

}
