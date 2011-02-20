/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.PropertyAccessor;
import org.mvel2.compiler.CompiledAccExpression;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.MVEL.compileSetExpression;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.ParseTools.*;

/**
 * @author Christopher Brock
 */
public class AssignmentNode extends ASTNode implements Assignment {
    private String varName;
    private transient CompiledAccExpression accExpr;

    private char[] indexTarget;
    private String index;

    // private char[] stmt;
    private ExecutableStatement statement;
    private boolean col = false;


    public AssignmentNode(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
        this.expr = expr;
        this.start = start;
        this.offset = offset;

        int assignStart;

        if ((assignStart = find(expr, start, offset, '=')) != -1) {
            this.varName = createStringTrimmed(expr, start, assignStart - start);

            this.start = skipWhitespace(expr, assignStart + 1, pCtx);
            if (this.start >= start+offset) {
                throw new CompileException("unexpected end of statement", expr, assignStart+1);
            }

            this.offset = offset - (this.start - start);

            if ((fields & COMPILE_IMMEDIATE) != 0) {
                this.egressType = (statement = (ExecutableStatement)
                        subCompileExpression(expr, this.start, this.offset, pCtx)).getKnownEgressType();
            }

            if (col = ((endOfName = findFirst('[', 0, this.varName.length(), indexTarget = this.varName.toCharArray())) > 0)) {
                if (((this.fields |= COLLECTION) & COMPILE_IMMEDIATE) != 0) {
                    accExpr = (CompiledAccExpression) compileSetExpression(indexTarget, pCtx);
                }

                this.varName = new String(expr, start, endOfName);
                index = new String(indexTarget, endOfName, indexTarget.length - endOfName);
            }


            try {
                checkNameSafety(this.varName);
            }
            catch (RuntimeException e) {
                throw new CompileException(e.getMessage(), expr, start);
            }
        }
        else {
            try {
                checkNameSafety(this.varName = new String(expr, start, offset));
            }
            catch (RuntimeException e) {
                throw new CompileException(e.getMessage(), expr, start);
            }
        }

        if ((fields & COMPILE_IMMEDIATE) != 0) {
            pCtx.addVariable(this.varName, egressType);
        }
    }

    public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
        if (accExpr == null && indexTarget != null) {
            accExpr = (CompiledAccExpression) compileSetExpression(indexTarget);
        }

        if (col) {
            return accExpr.setValue(ctx, thisValue, factory, statement.getValue(ctx, thisValue, factory));
        }
        else if (statement != null) {
            if (factory == null)
                throw new CompileException("cannot assign variables; no variable resolver factory available", expr, start);
            return factory.createVariable(varName, statement.getValue(ctx, thisValue, factory)).getValue();
        }
        else {
            if (factory == null)
                throw new CompileException("cannot assign variables; no variable resolver factory available", expr, start);
            factory.createVariable(varName, null);
            return null;
        }
    }

    public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
        checkNameSafety(varName);

        if (col) {
            PropertyAccessor.set(factory.getVariableResolver(varName).getValue(), factory, index, ctx = MVEL.eval(expr, start, offset, ctx, factory));
        }
        else {
            return factory.createVariable(varName, MVEL.eval(expr, start, offset, ctx, factory)).getValue();
        }

        return ctx;
    }


    public String getAssignmentVar() {
        return varName;
    }

    public char[] getExpression() {
        return subset(expr, start, offset);
    }

    public boolean isNewDeclaration() {
        return false;
    }

    public void setValueStatement(ExecutableStatement stmt) {
        this.statement = stmt;
    }

    @Override
    public String toString() {
        return varName + " = " + new String(expr, start, offset);
    }
}
