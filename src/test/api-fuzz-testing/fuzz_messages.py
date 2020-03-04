# -*- coding: utf-8 -*-
import json
import logging,os

from fuzzer.fuzzer import fuzz_multithreaded_websocket, fuzz_multithreaded_atom
from tokenizer.particles import SAMPLE_ATOM
from utils.__init_ import recursive_items
from tokenizer.particles import particle_group_to_big_size
from tokenizer.quotes import tokenize_double_quotes

#
#   Configure logging
#
logging.basicConfig(level=logging.DEBUG,
                    format='[%(asctime)s][%(levelname)-8s] %(message)s',
                    datefmt='%m-%d %H:%M',
                    filename='output.log',
                    filemode='w')

console = logging.StreamHandler()
console.setLevel(logging.INFO)
formatter = logging.Formatter('[%(asctime)s][%(levelname)-8s] %(message)s')
console.setFormatter(formatter)
logging.getLogger('').addHandler(console)

#
#   User configured parameters
#

# The websocket address, including the protocol, for example:
#
#       ws://localhost
#       wss://localhost
#
ws_address = 'ws://35.177.35.134:8080/rpc'

# The proxy server used to send the messages. This is very useful
# Set the os environment HTTP_PROXY to true if  proxy is used
# os.environ["HTTP_PROXY"] = "True"
http_proxy_host = 'localhost'
http_proxy_port = '8090'

# Log path, all messages are logged in different files
log_path = 'output/'

# Websocket authentication message. The tool will send the authentication
# message (if included in messages below) and wait for `session_active_message`
# before sending `message`
auth_message = ''
session_active_message = ''

# The message to send to the websocket (after fuzzing)
message = open(SAMPLE_ATOM, 'r').read()

# When fuzzing `message` ignore these tokens. The tokens are part of the original
# message which shouldn't be replaced with the payloads. For example, if the
# message is:
#
#   {"foo": "bar"}
#
# And the `ignore_tokens` list contains "bar", then the fuzzer is not going to
# send payloads in "bar" but it will in "foo".
keys = recursive_items(json.loads(message))
ignore_tokens = [key for key, value in keys]

# The list containing messages to be sent to the websocket. In some cases
# You need to send two or more messages to set a specific remote state, and
# then you send the attack
init_messages = [auth_message]

# The messages to be fuzzed, these are sent in different websocket connections
# after sending the `init_messages`.
#
# Each message is fuzzed using `create_tokenized_messages`. This tokenizer
# function, together with `replace_token_in_json` needs to be customized
# if your websocket messages are NOT JSON.
original_messages = [message]

# When doing analysis of the websocket responses to try to identify exceptions
# and other errors, ignore these errors since they are common for the
# application under test
ignore_errors = []

fuzz_multithreaded_atom(ws_address,
                        init_messages,
                        original_messages,
                        ignore_tokens,
                        ignore_errors,
                        log_path,
                        http_proxy_host,
                        http_proxy_port,
                        [
                            particle_group_to_big_size
                        ]
                        )

print ("Fuzzing biz size messages test finished")

# fuzz_multithreaded_websocket(ws_address,
#                              init_messages,
#                              original_messages,
#                              ignore_tokens,
#                              ignore_errors,
#                              log_path,
#                              http_proxy_host,
#                              http_proxy_port,
#                              [
#                                  tokenize_double_quotes
#                              ])
