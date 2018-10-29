class Binding:
    def __init__(self, token, implementation):
        self.implementation = implementation
        self.token = token


class Component:
    def __init__(self, bindings, name, description):
        self.bindings = bindings
        self.name = name
        self.description = description
