// Less configuration
var gulp = require('gulp');
var less = require('gulp-less');

gulp.task('less', function() {
    gulp.src('styles.less')
        .pipe(less())
        .pipe(gulp.dest('../public/css'))
});

gulp.task('default', ['less'], function() {
    gulp.watch('*.less', ['less']);
})