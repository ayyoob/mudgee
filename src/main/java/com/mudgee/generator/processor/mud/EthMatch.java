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
public class EthMatch {

	@JsonProperty("destination-mac-address")
	private String dstMacAddress;

	@JsonProperty("source-mac-address")
	private String srcMacAddress;

	@JsonProperty("ethertype")
	private String etherType;

	public String getDstMacAddress() {
		return dstMacAddress;
	}

	public void setDstMacAddress(String dstMacAddress) {
		this.dstMacAddress = dstMacAddress;
	}

	public String getSrcMacAddress() {
		return srcMacAddress;
	}

	public void setSrcMacAddress(String srcMacAddress) {
		this.srcMacAddress = srcMacAddress;
	}

	public String getEtherType() {
		return etherType;
	}

	public void setEtherType(String etherType) {
		this.etherType = etherType;
	}
}
