import argparse
import json


def get_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("-n", "--nServers", dest="n",
                        help="Number of servers")
    parser.add_argument("-u", "--nUsers", dest="nUsers",
                        help="Number of users")
    parser.add_argument("-a", "--nAdmins", dest="nAdmins",
                        help="Number of admins")
    parser.add_argument("-t", "--target1", dest="target1",
                        help="Target IP 1")
    parser.add_argument("-q", "--target2", dest="target2",
                        help="Target IP 2")
    parser.add_argument("-p", "--prefix", dest="prefix",
                        help="Client name prefix")
    return parser.parse_args()


if __name__ == '__main__':
    options = get_arguments()

    nServers = int(options.n)
    nUsers = int(options.nUsers)
    nAdmins = int(options.nAdmins)
    serverIP1 = options.target1
    serverIP2 = options.target2
    prefix = options.prefix

    if serverIP1 is None:
        print("Need some params!\n")
    elif serverIP1 is not None and serverIP2 is None:
        serverIP2 = serverIP1

    for i in range(nUsers):
        clientNum = i + 1
        cluster = {}
        for j in range(nServers):
            nServer = j + 1
            localIP = serverIP1 if j <= nServers // 2 else serverIP2
            cluster[prefix + "server" + str(nServer)] = {
                "name": prefix + "server" + str(nServer),
                "serverIP": localIP,
                "port": 4040 + nServer,
                "registryPort": 1099
            }

        configuration = {
            "name": prefix + "user" + str(clientNum),
            "clusterConfiguration": cluster
        }

        with open(prefix + "user" + str(clientNum) + ".json", 'w') as file:
            json.dump(configuration, file)

    for i in range(nAdmins):
        clientNum = i + 1
        cluster = {}
        for j in range(nServers):
            nServer = j + 1
            localIP = serverIP1 if j <= nServers // 2 else serverIP2
            cluster[prefix + "server" + str(nServer)] = {
                "name": prefix + "server" + str(nServer),
                "serverIP": localIP,
                "port": 4040 + nServer,
                "registryPort": 1099
            }

        configuration = {
            "name": prefix + "admin" + str(clientNum),
            "clusterConfiguration": cluster,
            "isAdmin": True
        }

        with open(prefix + "admin" + str(clientNum) + ".json", 'w') as file:
            json.dump(configuration, file)
