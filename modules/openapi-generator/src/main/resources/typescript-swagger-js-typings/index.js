var spec = require('./api.json');
var Swagger = require('swagger-client');

module.exports = function (options) {
    const { baseUrl, ...rest } = options;

    if (baseUrl) {
        spec.servers = [{ url: baseUrl }];
    }

    return Swagger({ spec, ...rest });
};