# SecureChatServer
Creates a SecureChatServer on the specified port and log directory. In its current state, it receives messages as byte[]s preceded with an enum that indicates the type of data. The server can currently handle nicknames, SecureConnection requests, and regular messages. The server also creates log files, both regular txt and XML-formatted.
