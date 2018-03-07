/*
 * Copyright (c) 2018, UNSW. (https://www.unsw.edu.au/) All Rights Reserved.
 *
 * UNSW. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.mudgee.generator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Match {

	@JsonProperty("ietf-mud:mud")
	private IetfMudMatch ietfMudMatch;

	@JsonProperty("l3")
	private L3Match l3Match;

	@JsonProperty("l4")
	private L4Match l4Match;

	public L3Match getL3Match() {
		return l3Match;
	}

	public void setL3Match(L3Match l3Match) {
		this.l3Match = l3Match;
	}

	public L4Match getL4Match() {
		return l4Match;
	}

	public void setL4Match(L4Match l4Match) {
		this.l4Match = l4Match;
	}

	public IetfMudMatch getIetfMudMatch() {
		return ietfMudMatch;
	}

	public void setIetfMudMatch(IetfMudMatch ietfMudMatch) {
		this.ietfMudMatch = ietfMudMatch;
	}
}
