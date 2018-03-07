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

import com.fasterxml.jackson.annotation.JsonProperty;

public class MudSpec {

	@JsonProperty("ietf-mud:mud")
	private  IetfMud ietfMud;

	@JsonProperty("ietf-access-control-list:access-lists")
	private IetfAccessControlListHolder accessControlList;

	public IetfMud getIetfMud() {
		return ietfMud;
	}

	public void setIetfMud(IetfMud ietfMud) {
		this.ietfMud = ietfMud;
	}

	public IetfAccessControlListHolder getAccessControlList() {
		return accessControlList;
	}

	public void setAccessControlList(IetfAccessControlListHolder accessControlList) {
		this.accessControlList = accessControlList;
	}
}
