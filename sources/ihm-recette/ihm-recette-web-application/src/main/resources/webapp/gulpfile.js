'use strict';

var pack = require('./package.json');
var gulp = require('gulp');
var bower = require('gulp-bower');
var concat = require('gulp-concat');
var gulpsync = require('gulp-sync')(gulp);
var del = require('del');
var zip = require('gulp-zip');
var gulpif = require('gulp-if');
var minifyHtml = require("gulp-minify-html");
var connect = require('gulp-connect');
var proxy = require('http-proxy-middleware');
var jshint = require('gulp-jshint');
var cleanCSS = require('gulp-clean-css');
var minifyJS = require("gulp-uglify");
var ngAnnotate = require('gulp-ng-annotate');
var Server = require('karma').Server;
var karma = require('karma');


var production = true;

var config = {
    assets: [
        '/bower_components/bootstrap-material-design-icons/fonts/*'
    ],
    sourceDirectory: 'app',
    testDirectory: 'test',
    targetDirectory: 'dist'
};

gulp.task('minifyHtml', function () {
    gulp.src('./**/*.html')
        .pipe(minifyHtml())
        .pipe(gulp.dest(config.targetDirectory));
});

gulp.task('package', gulpsync.sync(['bower', /*'lint';*/ 'build' /* 'version'*/]), function () {
    var name = pack.name + '-' + pack.version.match(/(\d+\.\d+).*/)[1] + '.zip';
    return gulp.src([config.targetDirectory + '/**/*'])
        .pipe(zip(name))
        .pipe(gulp.dest('./'));
});

gulp.task('clean', function () {
    return del([config.targetDirectory, 'vitam-*.zip', 'bower_components']);
});

gulp.task('bower', function () {
    bower().pipe(gulp.dest(config.targetDirectory + '/vendor'));
});

gulp.task('build:css', function () {
    return gulp.src(config.sourceDirectory + '/css/*.css')
        .pipe(gulpif(production, cleanCSS()))
        .pipe(gulp.dest(config.targetDirectory + '/css'));
});

gulp.task('build:html', function () {
    return gulp.src([config.sourceDirectory + '/**/*.html', /*'!' +*/ config.sourceDirectory + '/index.html', '!' + config.sourceDirectory + '/node_modules/**'])
        .pipe(gulpif(production, minifyHtml()))
        .pipe(gulp.dest(config.targetDirectory));
});

gulp.task('build:js', gulpsync.sync(['bower']), function () {
    return gulp.src(config.sourceDirectory + '/**/*.js')
        .pipe(ngAnnotate())
        .pipe(gulpif(production, minifyJS()))
        .pipe(gulp.dest(config.targetDirectory));
});

gulp.task('build:assets', function () {
    return gulp.src(
        [config.sourceDirectory + '/images/*', config.sourceDirectory + '/static/*', config.sourceDirectory + '/css/fonts/*'],
        {base: config.sourceDirectory}
    ).pipe(gulp.dest(config.targetDirectory))
});

gulp.task('build:vendor-assets', function () {
    return gulp.src(config.sourceDirectory + '/bower_components/bootstrap-material-design-icons/fonts/*')
        .pipe(gulp.dest(config.targetDirectory + '/bower_components/bootstrap-material-design-icons/fonts/'));
});

gulp.task('vendor:css', function () {
    return gulp.src('./bower_components/**/*.css')
        .pipe(gulp.dest(config.targetDirectory + '/vendor'));
});


gulp.task('build', ['build:css', 'build:assets', 'build:vendor-assets', 'build:html', /*'build:index', 'build:copyimg'*/ 'vendor:css', 'build:js'], function () {
});

gulp.task('default', ['serve']);

function serve() {
    connect.server({
        root: ['dist/'],
        port: 9000,
        livereload: true,
        middleware: function (connect, opt) {
            return [
                proxy('/ihm-recette', {
                    target: 'http://localhost:8082',
                    changeOrigin: true
                })
            ]
        }
    });
}

gulp.task('serve', ['watch'], function () {
    production = false;
    serve();
});

gulp.task('watch', ['build'], function () {
    gulp.watch(config.sourceDirectory + '/**/*.js', ['reload:js']);
    gulp.watch(config.sourceDirectory + '/**/*.html', ['reload:html']);
    gulp.watch(config.sourceDirectory + '/**/*.css', ['reload:css']);
});

gulp.task('reload', ['reload:css', 'reload:js', 'reload:html']);

gulp.task('reload:js', ['build:js'], function () {
    return gulp.src(config.targetDirectory + '/**/*.js')
        .pipe(connect.reload());
});

gulp.task('reload:html', ['build:html'/*, 'build:index'*/], function () {
    return gulp.src(config.targetDirectory + '/**/*.html')
        .pipe(connect.reload());
});

gulp.task('reload:css', ['build:css'], function () {
    return gulp.src(config.sourceDirectory + '/**/*.css')
        .pipe(connect.reload());
});

gulp.task('lint', function () {
    return gulp.src(config.sourceDirectory + '/**/*.js')
        .pipe(jshint())
        .pipe(jshint.reporter('jshint-stylish'))
        .pipe(jshint.reporter('fail'));
});

// TODO: add build on next step
gulp.task('test', function (cb) {
    new Server.start({
        configFile: __dirname + '/karma.conf.js',
        singleRun: true
    }, function () {
        cb()
    });
});
