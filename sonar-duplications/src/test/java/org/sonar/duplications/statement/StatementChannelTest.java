/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2011 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.duplications.statement;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.duplications.statement.matcher.AnyTokenMatcher;
import org.sonar.duplications.statement.matcher.TokenMatcher;
import org.sonar.duplications.token.Token;
import org.sonar.duplications.token.TokenQueue;

public class StatementChannelTest {

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotAcceptNull() {
    StatementChannel.create((TokenMatcher[]) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotAcceptEmpty() {
    StatementChannel.create(new TokenMatcher[] {});
  }

  @Test
  public void shouldPushForward() {
    TokenQueue tokenQueue = mock(TokenQueue.class);
    TokenMatcher matcher = mock(TokenMatcher.class);
    List<Statement> output = mock(List.class);
    StatementChannel channel = StatementChannel.create(matcher);

    assertThat(channel.consume(tokenQueue, output), is(false));
    ArgumentCaptor<List> matchedTokenList = ArgumentCaptor.forClass(List.class);
    verify(matcher).matchToken(Mockito.eq(tokenQueue), matchedTokenList.capture());
    verifyNoMoreInteractions(matcher);
    verify(tokenQueue).pushForward(matchedTokenList.getValue());
    verifyNoMoreInteractions(tokenQueue);
    verifyNoMoreInteractions(output);
  }

  @Test
  public void shouldCreateStatement() {
    Token token = new Token("a", 1, 1);
    TokenQueue tokenQueue = spy(new TokenQueue(Arrays.asList(token)));
    TokenMatcher matcher = spy(new AnyTokenMatcher());
    StatementChannel channel = StatementChannel.create(matcher);
    List<Statement> output = mock(List.class);

    assertThat(channel.consume(tokenQueue, output), is(true));
    verify(matcher).matchToken(Mockito.eq(tokenQueue), Mockito.anyList());
    verifyNoMoreInteractions(matcher);
    ArgumentCaptor<Statement> statement = ArgumentCaptor.forClass(Statement.class);
    verify(output).add(statement.capture());
    assertThat(statement.getValue().getValue(), is("a"));
    assertThat(statement.getValue().getStartLine(), is(1));
    assertThat(statement.getValue().getEndLine(), is(1));
    verifyNoMoreInteractions(output);
  }

  @Test
  public void shouldNotCreateStatement() {
    TokenQueue tokenQueue = spy(new TokenQueue(Arrays.asList(new Token("a", 1, 1))));
    TokenMatcher matcher = spy(new AnyTokenMatcher());
    StatementChannel channel = StatementChannel.create(matcher);
    List<Statement> output = mock(List.class);

    assertThat(channel.consume(tokenQueue, output), is(true));
    verify(matcher).matchToken(Mockito.eq(tokenQueue), Mockito.anyList());
    verifyNoMoreInteractions(matcher);
    verify(output).add(Mockito.any(Statement.class));
    verifyNoMoreInteractions(output);
  }

}
