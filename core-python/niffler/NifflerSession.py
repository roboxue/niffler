class NifflerSession:
    __storage__ = None  # type: Dict[Token, Any]

    def __init__(self):
        self.__storage__ = {}

    def get(self, token):
        return self.__storage__[token]

    def set(self, token, value):
        self.__storage__[token] = value