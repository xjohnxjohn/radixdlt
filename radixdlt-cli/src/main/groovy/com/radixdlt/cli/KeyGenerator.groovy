/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.radixdlt.cli

import com.radixdlt.client.application.identity.RadixIdentities
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * This command generates new private key and prints it to standard output.
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli generate-key -p=<password>
 * </pre>
 * The password is required and it should not be empty.
 */
@Command(name = "generate-key", mixinStandardHelpOptions = true,
		description = "Generate key")
class KeyGenerator implements Runnable {

	@Option(names = ["-p", "--password"], paramLabel = "PASSWORD", description = "password", required = true)
	String password

	@Override
	void run() {
		if (password == null || password.isBlank()) {
			println "The password must be provided"
			return
		}

		PrintWriter writer = new PrintWriter(System.out)
		//TODO: we need use other type of keystore here as well
		RadixIdentities.createNewEncryptedIdentity(writer, password)
		writer.flush()
		writer.close()
		println "Done"
	}
}
