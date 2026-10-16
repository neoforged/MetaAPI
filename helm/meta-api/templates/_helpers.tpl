{{/* Chart label used for helm.sh/chart */}}
{{- define "meta-api.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/* Short name of the chart (truncated to 63 chars, RFC1123-safe) */}}
{{- define "meta-api.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/* Fully qualified name of the chart (used for resource names) */}}
{{- define "meta-api.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/* Standard metadata labels */}}
{{- define "meta-api.labels" -}}
helm.sh/chart: {{ include "meta-api.chart" . }}
app.kubernetes.io/name: {{ include "meta-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: metaapi
{{- end }}

{{/* Labels used for Service selector / Deployment matchLabels */}}
{{- define "meta-api.selectorLabels" -}}
app.kubernetes.io/name: {{ include "meta-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
