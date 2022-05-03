import argparse
import json


def get_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("-n", "--n", dest="n",
                        help="Number of servers")
    parser.add_argument("-t", "--target1", dest="target1",
                        help="Target IP 1")
    parser.add_argument("-q", "--target2", dest="target2",
                        help="Target IP 2")
    parser.add_argument("-p", "--prefix", dest="prefix",
                        help="Server name prefix")
    return parser.parse_args()


if __name__ == '__main__':
    options = get_arguments()

    nServers = int(options.n)
    serverIP1 = options.target1
    serverIP2 = options.target2
    prefix = options.prefix

    if serverIP1 is None:
        print("Need some params!\n")
    elif serverIP1 is not None and serverIP2 is None:
        serverIP2 = serverIP1

    for i in range(nServers):
        serverNum = i + 1
        ownIp = serverIP1 if i <= nServers // 2 else serverIP2
        cluster = {}
        for j in range(nServers):
            if j != i:
                localServer = j + 1
                localIP = serverIP1 if j <= nServers // 2 else serverIP2
                cluster[prefix + "server" + str(localServer)] = {
                    "name": prefix + "server" + str(localServer),
                    "serverIP": localIP,
                    "port": 4040 + localServer,
                    "registryPort": 1099
                }

        configuration = {
            "name": prefix + "server" + str(serverNum),
            "serverIP": ownIp,
            "port": 4040 + serverNum,
            "registryPort": 1099,
            "cluster": cluster,
            "maxLogLength": 20
        }

        with open(prefix + "server" + str(serverNum) + ".json", 'w') as file:
            json.dump(configuration, file)
