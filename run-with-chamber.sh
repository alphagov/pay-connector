#!/usr/bin/env bash

if [[ $# -eq 0 ]]
then 
    AWS_REGION="${ECS_AWS_REGION}" chamber exec "${ECS_SERVICE}" -- ./docker-startup.sh
else
    AWS_REGION="${ECS_AWS_REGION}" chamber exec "${ECS_SERVICE}" --  "$@"
fi
