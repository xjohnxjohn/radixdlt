from tokenizer.quotes import tokenize_double_quotes
from tokenizer.particles import particle_group_to_big_size
TOKENIZER_METHODS = [
    #tokenize_method_name
   # create_fuzzed_particle_group,
   tokenize_double_quotes,
]


def create_tokenized_messages(original_message, ignore_tokens,methods = None):
    tokenized_messages = []
    tokeniser_methods = methods if methods else TOKENIZER_METHODS
    for tokenizer_method in tokeniser_methods:
        tokenized_messages.extend(tokenizer_method(original_message, ignore_tokens))
    return tokenized_messages